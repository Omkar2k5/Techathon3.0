#[allow(unused_imports)]
use actix_web::{post, web, HttpResponse};
use actix_multipart::Multipart;
use mongodb::bson::{doc, oid::ObjectId};
use futures::TryStreamExt;
use std::io::Write;
use chrono::Utc;
use crate::lab_module::mongo_connection::get_db;

#[post("/upload")]
pub async fn upload_file(mut payload: Multipart) -> HttpResponse {
    let db = get_db();

    let mut assignment_id_str = String::new();
    let mut subject_id_str = String::new();
    let mut student_id_str = String::new();
    let mut roll_no = String::new();
    let mut file_bytes: Vec<u8> = vec![];
    let mut file_name = String::new();

    while let Ok(Some(mut field)) = payload.try_next().await {
        let name = field.name().to_string();
        let mut data = Vec::new();
        while let Ok(Some(chunk)) = field.try_next().await {
            data.extend_from_slice(&chunk);
        }
        match name.as_str() {
            "assignment_id" => assignment_id_str = String::from_utf8_lossy(&data).to_string(),
            "subject_id" => subject_id_str = String::from_utf8_lossy(&data).to_string(),
            "student_id" => student_id_str = String::from_utf8_lossy(&data).to_string(),
            "roll_no" => roll_no = String::from_utf8_lossy(&data).to_string(),
            "file" => {
                let cd = field.content_disposition();
                if let Some(fname) = cd.get_filename() {
                    file_name = fname.to_string();
                }
                file_bytes = data;
            }
            _ => {}
        }
    }

    // Parse IDs
    let assignment_id = match ObjectId::parse_str(&assignment_id_str) {
        Ok(id) => id,
        Err(_) => return HttpResponse::BadRequest().json(serde_json::json!({"error":"Invalid assignment_id"})),
    };
    let subject_id = match ObjectId::parse_str(&subject_id_str) {
        Ok(id) => id,
        Err(_) => return HttpResponse::BadRequest().json(serde_json::json!({"error":"Invalid subject_id"})),
    };
    let student_id = match ObjectId::parse_str(&student_id_str) {
        Ok(id) => id,
        Err(_) => return HttpResponse::BadRequest().json(serde_json::json!({"error":"Invalid student_id"})),
    };

    // Fetch assignment — validate is_active and deadline
    let assignments_col = db.collection::<mongodb::bson::Document>("assignments");
    let assignment_doc = match assignments_col.find_one(doc! { "_id": assignment_id }, None).await {
        Ok(Some(d)) => d,
        _ => return HttpResponse::NotFound().json(serde_json::json!({"error":"Assignment not found"})),
    };

    let is_active = assignment_doc.get_bool("is_active").unwrap_or(false);
    if !is_active {
        return HttpResponse::Forbidden().json(serde_json::json!({"error":"Assignment is not active"}));
    }

    let deadline_str = assignment_doc.get_str("deadline").unwrap_or("");
    if let Ok(deadline) = chrono::DateTime::parse_from_rfc3339(deadline_str) {
        if Utc::now() > deadline {
            return HttpResponse::Forbidden().json(serde_json::json!({"error":"Deadline has passed"}));
        }
    }

    // Validate file extension
    let ext = file_name.rsplit('.').next().map(|e| format!(".{}", e.to_lowercase())).unwrap_or_default();
    let allowed: Vec<String> = assignment_doc.get_array("allowed_file_types")
        .map(|arr| arr.iter().filter_map(|b| b.as_str().map(String::from)).collect())
        .unwrap_or_default();
    if !allowed.contains(&ext) {
        return HttpResponse::BadRequest().json(serde_json::json!({
            "error": format!("File type '{}' not allowed. Allowed: {}", ext, allowed.join(", "))
        }));
    }

    // Check enrollment
    let enrollments_col = db.collection::<mongodb::bson::Document>("subject_enrollments");
    if let Ok(None) = enrollments_col.find_one(doc! { "subject_id": subject_id, "student_id": student_id }, None).await {
        return HttpResponse::Forbidden().json(serde_json::json!({"error":"Student not enrolled in this subject"}));
    }

    // Save file
    let dir = format!("lab_storage/assignment_{}/submissions", assignment_id_str);
    let _ = std::fs::create_dir_all(&dir);
    let ts = Utc::now().timestamp();
    let saved_path = format!("{}/{}_{}{}", dir, roll_no, ts, ext);
    match std::fs::File::create(&saved_path) {
        Ok(mut f) => { let _ = f.write_all(&file_bytes); }
        Err(e) => return HttpResponse::InternalServerError().json(serde_json::json!({"error": e.to_string()})),
    }

    // Insert submission document
    let submissions_col = db.collection::<mongodb::bson::Document>("submissions");
    let now = Utc::now().to_rfc3339();
    let sub_doc = doc! {
        "assignment_id": assignment_id,
        "subject_id": subject_id,
        "student_id": student_id,
        "roll_no": &roll_no,
        "file_path": &saved_path,
        "submitted_at": &now,
        "status": "submitted",
    };
    match submissions_col.insert_one(sub_doc, None).await {
        Ok(_) => HttpResponse::Ok().json(serde_json::json!({ "status": "success", "file": saved_path })),
        Err(e) => HttpResponse::InternalServerError().json(serde_json::json!({"error": e.to_string()})),
    }
}
