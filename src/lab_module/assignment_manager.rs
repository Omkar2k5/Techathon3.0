use actix_web::{get, post, patch, web, HttpResponse};
use actix_multipart::Multipart;
use mongodb::bson::{doc, oid::ObjectId};
use serde::{Deserialize, Serialize};
use chrono::Utc;
use futures::TryStreamExt;
use std::io::Write;
use crate::lab_module::mongo_connection::get_db;

#[derive(Debug, Serialize, Deserialize)]
pub struct AssignmentDoc {
    pub subject_id: ObjectId,
    pub assignment_name: String,
    pub sample_file_path: Option<String>,
    pub allowed_file_types: Vec<String>,
    pub time_limit_minutes: i32,
    pub start_time: Option<String>,
    pub deadline: String,
    pub is_active: bool,
    pub created_by: ObjectId,
    pub created_at: String,
}

#[derive(Debug, Deserialize)]
pub struct EditAssignmentRequest {
    pub assignment_name: Option<String>,
    pub time_limit_minutes: Option<i32>,
    pub deadline: Option<String>,
    pub allowed_file_types: Option<Vec<String>>,
}

// POST /assignments/create
#[post("/assignments/create")]
pub async fn create_assignment(mut payload: Multipart) -> HttpResponse {
    let db = get_db();
    let col = db.collection::<mongodb::bson::Document>("assignments");

    let mut subject_id_str = String::new();
    let mut assignment_name = String::new();
    let mut allowed_file_types: Vec<String> = vec![];
    let mut time_limit: i32 = 60;
    let mut deadline = String::new();
    let mut created_by_str = String::new();
    let mut sample_file_path: Option<String> = None;

    while let Ok(Some(mut field)) = payload.try_next().await {
        let name = field.name().to_string();
        let mut data = Vec::new();
        while let Ok(Some(chunk)) = field.try_next().await {
            data.extend_from_slice(&chunk);
        }
        match name.as_str() {
            "subject_id"         => subject_id_str = String::from_utf8_lossy(&data).to_string(),
            "assignment_name"    => assignment_name = String::from_utf8_lossy(&data).to_string(),
            "allowed_file_types" => {
                let s = String::from_utf8_lossy(&data).to_string();
                allowed_file_types = serde_json::from_str::<Vec<String>>(&s).unwrap_or_default();
            }
            "time_limit_minutes" => time_limit = String::from_utf8_lossy(&data).parse().unwrap_or(60),
            "deadline"           => deadline = String::from_utf8_lossy(&data).to_string(),
            "created_by"         => created_by_str = String::from_utf8_lossy(&data).to_string(),
            "sample_file" => {
                let cd = field.content_disposition();
                if let Some(fname) = cd.get_filename() {
                    let dir = format!("lab_storage/{}/{}", &subject_id_str, &assignment_name.replace(' ', "_"));
                    let _ = std::fs::create_dir_all(&dir);
                    let path = format!("{}/{}", dir, fname);
                    if let Ok(mut f) = std::fs::File::create(&path) {
                        let _ = f.write_all(&data);
                        sample_file_path = Some(path);
                    }
                }
            }
            _ => {}
        }
    }

    let subject_id = match ObjectId::parse_str(&subject_id_str) {
        Ok(id) => id,
        Err(_) => return HttpResponse::BadRequest().json(serde_json::json!({"error":"Invalid subject_id"})),
    };
    let created_by = match ObjectId::parse_str(&created_by_str) {
        Ok(id) => id,
        Err(_) => return HttpResponse::BadRequest().json(serde_json::json!({"error":"Invalid created_by"})),
    };

    let types_bson: Vec<_> = allowed_file_types.iter().map(|s| mongodb::bson::Bson::String(s.clone())).collect();
    let doc = doc! {
        "subject_id": subject_id,
        "assignment_name": &assignment_name,
        "sample_file_path": sample_file_path,
        "allowed_file_types": mongodb::bson::Bson::Array(types_bson),
        "time_limit_minutes": time_limit,
        "start_time": mongodb::bson::Bson::Null,
        "deadline": &deadline,
        "is_active": false,
        "created_by": created_by,
        "created_at": Utc::now().to_rfc3339(),
    };

    match col.insert_one(doc, None).await {
        Ok(res) => {
            let id = res.inserted_id.as_object_id().map(|id| id.to_hex()).unwrap_or_default();
            HttpResponse::Ok().json(serde_json::json!({ "id": id }))
        }
        Err(e) => HttpResponse::InternalServerError().json(serde_json::json!({"error": e.to_string()})),
    }
}

// PATCH /assignments/{id}  — edit name, time_limit, deadline, file types
#[patch("/assignments/{id}")]
pub async fn edit_assignment(path: web::Path<String>, body: web::Json<EditAssignmentRequest>) -> HttpResponse {
    let db = get_db();
    let col = db.collection::<mongodb::bson::Document>("assignments");
    let id = match ObjectId::parse_str(path.into_inner()) {
        Ok(id) => id,
        Err(_) => return HttpResponse::BadRequest().json(serde_json::json!({"error":"Invalid ID"})),
    };

    let mut set_doc = doc! {};
    if let Some(name) = &body.assignment_name {
        set_doc.insert("assignment_name", name);
    }
    if let Some(tl) = body.time_limit_minutes {
        set_doc.insert("time_limit_minutes", tl);
    }
    if let Some(dl) = &body.deadline {
        set_doc.insert("deadline", dl);
    }
    if let Some(types) = &body.allowed_file_types {
        let types_bson: Vec<_> = types.iter().map(|s| mongodb::bson::Bson::String(s.clone())).collect();
        set_doc.insert("allowed_file_types", mongodb::bson::Bson::Array(types_bson));
    }

    if set_doc.is_empty() {
        return HttpResponse::BadRequest().json(serde_json::json!({"error":"Nothing to update"}));
    }

    match col.update_one(doc! { "_id": id }, doc! { "$set": set_doc }, None).await {
        Ok(_) => HttpResponse::Ok().json(serde_json::json!({"status":"updated"})),
        Err(e) => HttpResponse::InternalServerError().json(serde_json::json!({"error": e.to_string()})),
    }
}

// POST /assignments/start/{id}
#[post("/assignments/start/{id}")]
pub async fn start_assignment(path: web::Path<String>) -> HttpResponse {
    let db = get_db();
    let col = db.collection::<mongodb::bson::Document>("assignments");
    let id = match ObjectId::parse_str(path.into_inner()) {
        Ok(id) => id,
        Err(_) => return HttpResponse::BadRequest().json(serde_json::json!({"error":"Invalid ID"})),
    };
    let update = doc! { "$set": { "is_active": true, "start_time": Utc::now().to_rfc3339() } };
    match col.update_one(doc! { "_id": id }, update, None).await {
        Ok(r) if r.matched_count == 0 => HttpResponse::NotFound().json(serde_json::json!({"error":"Assignment not found"})),
        Ok(_) => HttpResponse::Ok().json(serde_json::json!({ "status": "started" })),
        Err(e) => HttpResponse::InternalServerError().json(serde_json::json!({"error": e.to_string()})),
    }
}

// POST /assignments/close/{id}
#[post("/assignments/close/{id}")]
pub async fn close_assignment(path: web::Path<String>) -> HttpResponse {
    let db = get_db();
    let col = db.collection::<mongodb::bson::Document>("assignments");
    let id = match ObjectId::parse_str(path.into_inner()) {
        Ok(id) => id,
        Err(_) => return HttpResponse::BadRequest().json(serde_json::json!({"error":"Invalid ID"})),
    };
    let update = doc! { "$set": { "is_active": false } };
    let _ = col.update_one(doc! { "_id": id }, update, None).await;
    HttpResponse::Ok().json(serde_json::json!({ "status": "closed" }))
}

// GET /assignments/active  — works for both student_id and teacher_id query params
#[get("/assignments/active")]
pub async fn get_active_assignments(query: web::Query<std::collections::HashMap<String, String>>) -> HttpResponse {
    let db = get_db();
    let col = db.collection::<mongodb::bson::Document>("assignments");
    let subjects_col = db.collection::<mongodb::bson::Document>("subjects");
    let enrollments_col = db.collection::<mongodb::bson::Document>("subject_enrollments");
    let users_col = db.collection::<mongodb::bson::Document>("users");

    // ── Teacher mode: return active assignments for teacher's subjects ──
    if let Some(teacher_id_str) = query.get("teacher_id") {
        let teacher_id = match ObjectId::parse_str(teacher_id_str) {
            Ok(id) => id,
            Err(_) => return HttpResponse::BadRequest().json(serde_json::json!({"error":"Invalid teacher_id"})),
        };
        // Get teacher's subjects
        let mut subject_ids: Vec<ObjectId> = vec![];
        if let Ok(mut cur) = subjects_col.find(doc! { "teacher_id": teacher_id, "is_active": true }, None).await {
            while let Ok(Some(doc)) = cur.try_next().await {
                if let Ok(sid) = doc.get_object_id("_id") {
                    subject_ids.push(sid);
                }
            }
        }
        let sid_bson: Vec<_> = subject_ids.iter().map(|id| mongodb::bson::Bson::ObjectId(*id)).collect();
        let filter = doc! { "is_active": true, "subject_id": { "$in": sid_bson } };
        let mut results = vec![];
        if let Ok(mut cur) = col.find(filter, None).await {
            while let Ok(Some(doc)) = cur.try_next().await {
                results.push(serde_json::json!({
                    "id": doc.get_object_id("_id").map(|id| id.to_hex()).unwrap_or_default(),
                    "subject_id": doc.get_object_id("subject_id").map(|id| id.to_hex()).unwrap_or_default(),
                    "assignment_name": doc.get_str("assignment_name").unwrap_or(""),
                    "is_active": true,
                }));
            }
        }
        return HttpResponse::Ok().json(results);
    }

    // ── Student mode: return active assignments for enrolled subjects ──
    let student_id_str = query.get("student_id").cloned().unwrap_or_default();
    let student_id = match ObjectId::parse_str(&student_id_str) {
        Ok(id) => id,
        Err(_) => return HttpResponse::BadRequest().json(serde_json::json!({"error":"Invalid student_id"})),
    };

    // Get enrolled subject_ids
    let mut enrolled_subject_ids: Vec<ObjectId> = vec![];
    if let Ok(mut cur) = enrollments_col.find(doc! { "student_id": student_id }, None).await {
        while let Ok(Some(doc)) = cur.try_next().await {
            if let Ok(sid) = doc.get_object_id("subject_id") {
                enrolled_subject_ids.push(sid);
            }
        }
    }

    let enrolled_bson: Vec<_> = enrolled_subject_ids.iter().map(|id| mongodb::bson::Bson::ObjectId(*id)).collect();
    let filter = doc! { "is_active": true, "subject_id": { "$in": enrolled_bson } };

    let mut results = vec![];
    if let Ok(mut cur) = col.find(filter, None).await {
        while let Ok(Some(doc)) = cur.try_next().await {
            let id = doc.get_object_id("_id").map(|id| id.to_hex()).unwrap_or_default();
            let subject_id_oid = doc.get_object_id("subject_id").ok();
            let mut subject_name = String::new();
            let mut subject_code = String::new();
            let mut teacher_name = String::new();
            let mut teacher_id_hex = String::new();

            if let Some(sid) = subject_id_oid {
                if let Ok(Some(sdoc)) = subjects_col.find_one(doc! { "_id": sid }, None).await {
                    subject_name = sdoc.get_str("subject_name").unwrap_or("").to_string();
                    subject_code = sdoc.get_str("subject_code").unwrap_or("").to_string();
                    if let Ok(tid) = sdoc.get_object_id("teacher_id") {
                        teacher_id_hex = tid.to_hex();
                        if let Ok(Some(udoc)) = users_col.find_one(doc! { "_id": tid }, None).await {
                            teacher_name = udoc.get_str("name").unwrap_or("").to_string();
                        }
                    }
                }
            }

            let types: Vec<String> = doc.get_array("allowed_file_types").map(|arr| {
                arr.iter().filter_map(|b| b.as_str().map(|s| s.to_string())).collect()
            }).unwrap_or_default();

            results.push(serde_json::json!({
                "id": id,
                "subject_id": doc.get_object_id("subject_id").map(|id| id.to_hex()).unwrap_or_default(),
                "subject_name": subject_name,
                "subject_code": subject_code,
                "assignment_name": doc.get_str("assignment_name").unwrap_or(""),
                "allowed_file_types": types,
                "time_limit_minutes": doc.get_i32("time_limit_minutes").unwrap_or(60),
                "deadline": doc.get_str("deadline").unwrap_or(""),
                "start_time": doc.get_str("start_time").unwrap_or(""),
                "is_active": true,
                "has_sample": doc.get_str("sample_file_path").is_ok(),
                "teacher_name": teacher_name,
                "teacher_id": teacher_id_hex,
            }));
        }
    }
    HttpResponse::Ok().json(results)
}

// GET /assignments/subject/{subject_id}
#[get("/assignments/subject/{subject_id}")]
pub async fn get_subject_assignments(path: web::Path<String>) -> HttpResponse {
    let db = get_db();
    let col = db.collection::<mongodb::bson::Document>("assignments");
    let users_col = db.collection::<mongodb::bson::Document>("users");
    let subject_id = match ObjectId::parse_str(path.into_inner()) {
        Ok(id) => id,
        Err(_) => return HttpResponse::BadRequest().json(serde_json::json!({"error":"Invalid ID"})),
    };
    let filter = doc! { "subject_id": subject_id };
    let mut results = vec![];
    if let Ok(mut cur) = col.find(filter, None).await {
        while let Ok(Some(doc)) = cur.try_next().await {
            let id = doc.get_object_id("_id").map(|id| id.to_hex()).unwrap_or_default();
            let types: Vec<String> = doc.get_array("allowed_file_types").map(|arr| {
                arr.iter().filter_map(|b| b.as_str().map(|s| s.to_string())).collect()
            }).unwrap_or_default();

            // Resolve teacher name
            let mut teacher_name = String::new();
            if let Ok(tid) = doc.get_object_id("created_by") {
                if let Ok(Some(udoc)) = users_col.find_one(doc! { "_id": tid }, None).await {
                    teacher_name = udoc.get_str("name").unwrap_or("").to_string();
                }
            }

            results.push(serde_json::json!({
                "id": id,
                "assignment_name": doc.get_str("assignment_name").unwrap_or(""),
                "allowed_file_types": types,
                "time_limit_minutes": doc.get_i32("time_limit_minutes").unwrap_or(60),
                "deadline": doc.get_str("deadline").unwrap_or(""),
                "is_active": doc.get_bool("is_active").unwrap_or(false),
                "start_time": doc.get_str("start_time").unwrap_or(""),
                "has_sample": doc.get_str("sample_file_path").is_ok(),
                "created_at": doc.get_str("created_at").unwrap_or(""),
                "created_by": doc.get_object_id("created_by").map(|id| id.to_hex()).unwrap_or_default(),
                "teacher_name": teacher_name,
            }));
        }
    }
    HttpResponse::Ok().json(results)
}

// GET /assignments/{id}/sample
#[get("/assignments/{id}/sample")]
pub async fn download_sample(path: web::Path<String>) -> HttpResponse {
    let db = get_db();
    let col = db.collection::<mongodb::bson::Document>("assignments");
    let id = match ObjectId::parse_str(path.into_inner()) {
        Ok(id) => id,
        Err(_) => return HttpResponse::NotFound().finish(),
    };
    if let Ok(Some(doc)) = col.find_one(doc! { "_id": id }, None).await {
        if let Ok(path_str) = doc.get_str("sample_file_path") {
            if let Ok(data) = std::fs::read(path_str) {
                let fname = std::path::Path::new(path_str).file_name()
                    .and_then(|n| n.to_str()).unwrap_or("sample");
                return HttpResponse::Ok()
                    .append_header(("Content-Disposition", format!("attachment; filename=\"{}\"", fname)))
                    .body(data);
            }
        }
    }
    HttpResponse::NotFound().finish()
}
