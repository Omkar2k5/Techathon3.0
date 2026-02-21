use actix_web::{get, post, web, HttpResponse};
use actix_multipart::Multipart;
use mongodb::bson::{doc, oid::ObjectId, DateTime as BsonDateTime};
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
            "subject_id" => subject_id_str = String::from_utf8_lossy(&data).to_string(),
            "assignment_name" => assignment_name = String::from_utf8_lossy(&data).to_string(),
            "allowed_file_types" => {
                let s = String::from_utf8_lossy(&data).to_string();
                allowed_file_types = serde_json::from_str::<Vec<String>>(&s).unwrap_or_default();
            }
            "time_limit_minutes" => time_limit = String::from_utf8_lossy(&data).parse().unwrap_or(60),
            "deadline" => deadline = String::from_utf8_lossy(&data).to_string(),
            "created_by" => created_by_str = String::from_utf8_lossy(&data).to_string(),
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

#[post("/assignments/start/{id}")]
pub async fn start_assignment(path: web::Path<String>) -> HttpResponse {
    let db = get_db();
    let col = db.collection::<mongodb::bson::Document>("assignments");
    let id = match ObjectId::parse_str(path.into_inner()) {
        Ok(id) => id,
        Err(_) => return HttpResponse::BadRequest().json(serde_json::json!({"error":"Invalid ID"})),
    };
    let update = doc! { "$set": { "is_active": true, "start_time": Utc::now().to_rfc3339() } };
    let _ = col.update_one(doc! { "_id": id }, update, None).await;
    HttpResponse::Ok().json(serde_json::json!({ "status": "started" }))
}

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

#[get("/assignments/active")]
pub async fn get_active_assignments(query: web::Query<std::collections::HashMap<String, String>>) -> HttpResponse {
    let db = get_db();
    let col = db.collection::<mongodb::bson::Document>("assignments");
    let subjects_col = db.collection::<mongodb::bson::Document>("subjects");
    let enrollments_col = db.collection::<mongodb::bson::Document>("subject_enrollments");

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
            let subject_id = doc.get_object_id("subject_id").ok();
            let mut subject_name = String::new();
            let mut subject_code = String::new();
            if let Some(sid) = subject_id {
                if let Ok(Some(sdoc)) = subjects_col.find_one(doc! { "_id": sid }, None).await {
                    subject_name = sdoc.get_str("subject_name").unwrap_or("").to_string();
                    subject_code = sdoc.get_str("subject_code").unwrap_or("").to_string();
                }
            }
            let types: Vec<String> = doc.get_array("allowed_file_types").map(|arr| {
                arr.iter().filter_map(|b| b.as_str().map(|s| s.to_string())).collect()
            }).unwrap_or_default();

            let has_sample = doc.get_str("sample_file_path").is_ok();

            results.push(serde_json::json!({
                "id": id,
                "subject_id": doc.get_object_id("subject_id").map(|id| id.to_hex()).unwrap_or_default(),
                "subject_name": subject_name,
                "subject_code": subject_code,
                "assignment_name": doc.get_str("assignment_name").unwrap_or(""),
                "allowed_file_types": types,
                "time_limit_minutes": doc.get_i32("time_limit_minutes").unwrap_or(60),
                "deadline": doc.get_str("deadline").unwrap_or(""),
                "is_active": true,
                "has_sample": has_sample,
            }));
        }
    }
    HttpResponse::Ok().json(results)
}

#[get("/assignments/subject/{subject_id}")]
pub async fn get_subject_assignments(path: web::Path<String>) -> HttpResponse {
    let db = get_db();
    let col = db.collection::<mongodb::bson::Document>("assignments");
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
            results.push(serde_json::json!({
                "id": id,
                "assignment_name": doc.get_str("assignment_name").unwrap_or(""),
                "allowed_file_types": types,
                "time_limit_minutes": doc.get_i32("time_limit_minutes").unwrap_or(60),
                "deadline": doc.get_str("deadline").unwrap_or(""),
                "is_active": doc.get_bool("is_active").unwrap_or(false),
                "created_at": doc.get_str("created_at").unwrap_or(""),
            }));
        }
    }
    HttpResponse::Ok().json(results)
}

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
