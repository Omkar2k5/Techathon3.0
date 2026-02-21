use actix_web::{get, post, web, HttpResponse};
use mongodb::bson::{doc, oid::ObjectId};
use serde::{Deserialize, Serialize};
use chrono::Utc;
use crate::lab_module::mongo_connection::get_db;

#[derive(Debug, Serialize, Deserialize)]
pub struct Subject {
    #[serde(rename = "_id", skip_serializing_if = "Option::is_none")]
    pub id: Option<ObjectId>,
    pub subject_name: String,
    pub subject_code: String,
    pub teacher_id: ObjectId,
    pub is_active: bool,
    pub created_at: String,
}

#[derive(Debug, Deserialize)]
pub struct CreateSubjectRequest {
    pub subject_name: String,
    pub subject_code: String,
    pub teacher_id: String,
}

#[get("/subjects/teacher/{teacher_id}")]
pub async fn get_teacher_subjects(path: web::Path<String>) -> HttpResponse {
    let db = get_db();
    let col = db.collection::<mongodb::bson::Document>("subjects");
    let teacher_id = match ObjectId::parse_str(path.into_inner()) {
        Ok(id) => id,
        Err(_) => return HttpResponse::BadRequest().json(serde_json::json!({"error":"Invalid teacher ID"})),
    };
    let filter = doc! { "teacher_id": teacher_id, "is_active": true };
    match col.find(filter, None).await {
        Ok(mut cursor) => {
            let mut results = vec![];
            use futures::TryStreamExt;
            while let Ok(Some(doc)) = cursor.try_next().await {
                let id = doc.get_object_id("_id").map(|id| id.to_hex()).unwrap_or_default();
                results.push(serde_json::json!({
                    "id": id,
                    "subject_name": doc.get_str("subject_name").unwrap_or(""),
                    "subject_code": doc.get_str("subject_code").unwrap_or(""),
                }));
            }
            HttpResponse::Ok().json(results)
        }
        Err(e) => {
            eprintln!("LAB SUBJECTS: {}", e);
            HttpResponse::InternalServerError().json(serde_json::json!({"error":"DB error"}))
        }
    }
}

#[get("/subjects/student/{student_id}")]
pub async fn get_student_subjects(path: web::Path<String>) -> HttpResponse {
    let db = get_db();
    let enrollments = db.collection::<mongodb::bson::Document>("subject_enrollments");
    let subjects_col = db.collection::<mongodb::bson::Document>("subjects");
    let student_id = match ObjectId::parse_str(path.into_inner()) {
        Ok(id) => id,
        Err(_) => return HttpResponse::BadRequest().json(serde_json::json!({"error":"Invalid student ID"})),
    };

    let filter = doc! { "student_id": student_id };
    let mut subject_ids = vec![];
    use futures::TryStreamExt;
    if let Ok(mut cursor) = enrollments.find(filter, None).await {
        while let Ok(Some(doc)) = cursor.try_next().await {
            if let Ok(sid) = doc.get_object_id("subject_id") {
                subject_ids.push(sid);
            }
        }
    }

    let mut results = vec![];
    for sid in subject_ids {
        if let Ok(Some(doc)) = subjects_col.find_one(doc! { "_id": sid, "is_active": true }, None).await {
            let id = doc.get_object_id("_id").map(|id| id.to_hex()).unwrap_or_default();
            results.push(serde_json::json!({
                "id": id,
                "subject_name": doc.get_str("subject_name").unwrap_or(""),
                "subject_code": doc.get_str("subject_code").unwrap_or(""),
            }));
        }
    }
    HttpResponse::Ok().json(results)
}

#[post("/subjects/create")]
pub async fn create_subject(body: web::Json<CreateSubjectRequest>) -> HttpResponse {
    let db = get_db();
    let col = db.collection::<mongodb::bson::Document>("subjects");
    let teacher_id = match ObjectId::parse_str(&body.teacher_id) {
        Ok(id) => id,
        Err(_) => return HttpResponse::BadRequest().json(serde_json::json!({"error":"Invalid teacher ID"})),
    };
    let doc = doc! {
        "subject_name": &body.subject_name,
        "subject_code": &body.subject_code,
        "teacher_id": teacher_id,
        "is_active": true,
        "created_at": Utc::now().to_rfc3339(),
    };
    match col.insert_one(doc, None).await {
        Ok(res) => HttpResponse::Ok().json(serde_json::json!({ "id": res.inserted_id.to_string() })),
        Err(e) => HttpResponse::InternalServerError().json(serde_json::json!({"error": e.to_string()})),
    }
}
