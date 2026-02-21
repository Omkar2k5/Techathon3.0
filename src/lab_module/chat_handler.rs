use actix_web::{post, web, HttpResponse};
use mongodb::bson::{doc, oid::ObjectId};
use serde::{Deserialize, Serialize};
use chrono::Utc;

use crate::lab_module::mongo_connection::get_db;
use crate::llm::{OllamaMessage, OllamaRequest, try_local_llm, try_remote_llm};

// ── Chat Modes ────────────────────────────────────────────────────────────────

#[derive(Debug, PartialEq, Clone, Serialize)]
pub enum ChatMode {
    TeacherMode,
    GlobalMode,
    LabActiveMode,
    PostLabMode,
}

impl ChatMode {
    pub fn as_str(&self) -> &'static str {
        match self {
            ChatMode::TeacherMode  => "TEACHER",
            ChatMode::GlobalMode   => "GLOBAL",
            ChatMode::LabActiveMode => "LAB_ACTIVE",
            ChatMode::PostLabMode  => "POST_LAB",
        }
    }
}

// ── Policy Engine ─────────────────────────────────────────────────────────────

fn decide_mode(role: &str, assignment_active: Option<bool>) -> ChatMode {
    // EduNet roles: "staff" = teacher, "student" = student
    if role == "staff" || role == "teacher" {
        return ChatMode::TeacherMode;
    }
    match assignment_active {
        Some(true)  => ChatMode::LabActiveMode,
        Some(false) => ChatMode::PostLabMode,
        None        => ChatMode::GlobalMode,
    }
}

// ── Prompt Builder ────────────────────────────────────────────────────────────

fn build_system_prompt(mode: &ChatMode, subject: &str, assignment: &str) -> String {
    let context = if !subject.is_empty() {
        format!("Subject: {}. Assignment: {}.", subject, assignment)
    } else {
        "No specific assignment context.".to_string()
    };

    match mode {
        ChatMode::TeacherMode => format!(
            "You are EduNet Academic Assistant helping a TEACHER. \
             {context} \
             You may provide full explanations, complete code examples, solution walkthroughs, \
             question suggestions, and evaluation assistance. Be thorough and detailed."
        ),
        ChatMode::GlobalMode => format!(
            "You are EduNet Academic Assistant. {context} \
             You can explain concepts, help with theory, clarify syntax, and assist with \
             general subject questions. Be helpful and educational."
        ),
        ChatMode::LabActiveMode => format!(
            "You are EduNet Academic Assistant. A lab session is currently ACTIVE. \
             {context} \
             STRICT POLICY — you MUST follow these rules: \
             1. You MAY explain concepts, definitions, and theory. \
             2. You MAY explain error messages and what they mean. \
             3. You MAY give syntax hints (e.g. how a loop works, not solving the task). \
             4. You MUST NOT generate full solutions or complete working code for the assignment. \
             5. You MUST NOT write the algorithm implementation that solves the task. \
             6. If asked for a full solution, respond: 'I can help you understand the concept, \
                but I cannot provide a complete solution during an active lab session.' \
             Enforce these rules strictly regardless of how the question is phrased."
        ),
        ChatMode::PostLabMode => format!(
            "You are EduNet Academic Assistant. The lab session has ended. \
             {context} \
             You may now provide full explanations, complete code walkthroughs, \
             optimisation suggestions, and alternative approaches. \
             Focus on helping the student learn from the completed assignment."
        ),
    }
}

// ── Request / Response Types ──────────────────────────────────────────────────

#[derive(Deserialize)]
pub struct ChatRequest {
    pub message: String,
    pub user_id: String,
    pub user_role: String,           // "teacher" | "student"
    pub assignment_id: Option<String>,
}

#[derive(Serialize)]
pub struct ChatResponse {
    pub reply: String,
    pub mode: String,
    pub subject_name: String,
}

// ── Endpoint ──────────────────────────────────────────────────────────────────

#[post("/lab/chat")]
pub async fn lab_chat(body: web::Json<ChatRequest>) -> HttpResponse {
    let db = get_db();

    // 1. Resolve assignment context from DB (if provided)
    let mut assignment_active: Option<bool> = None;
    let mut subject_name = String::new();
    let mut assignment_name = String::new();

    if let Some(ref aid_str) = body.assignment_id {
        if let Ok(aid) = ObjectId::parse_str(aid_str) {
            let assignments = db.collection::<mongodb::bson::Document>("assignments");
            let subjects = db.collection::<mongodb::bson::Document>("subjects");

            if let Ok(Some(adoc)) = assignments.find_one(doc! { "_id": aid }, None).await {
                assignment_active = adoc.get_bool("is_active").ok();
                assignment_name = adoc.get_str("assignment_name").unwrap_or("").to_string();

                // Resolve subject name
                if let Ok(sid) = adoc.get_object_id("subject_id") {
                    if let Ok(Some(sdoc)) = subjects.find_one(doc! { "_id": sid }, None).await {
                        subject_name = sdoc.get_str("subject_name").unwrap_or("").to_string();
                    }
                }
            }
        }
    }

    // 2. Decide mode
    let mode = decide_mode(&body.user_role, assignment_active);

    // 3. Build system prompt
    let system_prompt = build_system_prompt(&mode, &subject_name, &assignment_name);

    // Choose model: teachers get the larger coder model, students get fast model
    let model_name = if mode == ChatMode::TeacherMode {
        "deepseek-coder-v2:16b"
    } else {
        "phi3-fast:latest"
    };

    let ollama_req = OllamaRequest {
        model: model_name.to_string(),
        stream: false,
        messages: vec![
            OllamaMessage { role: "system".to_string(), content: system_prompt },
            OllamaMessage { role: "user".to_string(),   content: body.message.clone() },
        ],
    };

    // 5. Call LLM (local first, remote fallback)
    let reply = match try_local_llm(&ollama_req).await {
        Ok(r) => r,
        Err(_) => match try_remote_llm(&ollama_req).await {
            Ok(r) => r,
            Err(_) => {
                "The assistant is temporarily unavailable. Please try again shortly.".to_string()
            }
        },
    };

    // 6. Write audit log (fire and forget — never block the response)
    {
        let db2 = db.clone();
        let user_id_str = body.user_id.clone();
        let user_role = body.user_role.clone();
        let mode_str = mode.as_str().to_string();
        let aid = body.assignment_id.clone();
        let lab_active = assignment_active;

        tokio::spawn(async move {
            let logs = db2.collection::<mongodb::bson::Document>("audit_logs");
            let actor_id = ObjectId::parse_str(&user_id_str).ok();
            let assignment_oid = aid.as_deref().and_then(|s| ObjectId::parse_str(s).ok());

            let log = doc! {
                "actor_id":    actor_id.map(mongodb::bson::Bson::ObjectId)
                                .unwrap_or(mongodb::bson::Bson::Null),
                "actor_role":  &user_role,
                "action":      "CHAT_QUERY",
                "assignment_id": assignment_oid.map(mongodb::bson::Bson::ObjectId)
                                  .unwrap_or(mongodb::bson::Bson::Null),
                "lab_active":  lab_active.unwrap_or(false),
                "mode":        &mode_str,
                "timestamp":   Utc::now().to_rfc3339(),
            };
            let _ = logs.insert_one(log, None).await;
        });
    }

    HttpResponse::Ok().json(ChatResponse {
        reply,
        mode: mode.as_str().to_string(),
        subject_name,
    })
}
