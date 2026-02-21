use actix_web::{get, web, HttpResponse};
use mongodb::bson::{doc, oid::ObjectId};
use futures::TryStreamExt;
use crate::lab_module::mongo_connection::get_db;


#[get("/assignments/{id}/submissions")]
pub async fn get_submissions(path: web::Path<String>) -> HttpResponse {
    let db = get_db();
    let col = db.collection::<mongodb::bson::Document>("submissions");
    let id = match ObjectId::parse_str(path.into_inner()) {
        Ok(id) => id,
        Err(_) => return HttpResponse::BadRequest().json(serde_json::json!({"error":"Invalid ID"})),
    };
    let filter = doc! { "assignment_id": id };
    let mut results = vec![];
    if let Ok(mut cur) = col.find(filter, None).await {
        while let Ok(Some(doc)) = cur.try_next().await {
            results.push(serde_json::json!({
                "id": doc.get_object_id("_id").map(|id| id.to_hex()).unwrap_or_default(),
                "roll_no": doc.get_str("roll_no").unwrap_or(""),
                "file_path": doc.get_str("file_path").unwrap_or(""),
                "submitted_at": doc.get_str("submitted_at").unwrap_or(""),
                "status": doc.get_str("status").unwrap_or("submitted"),
            }));
        }
    }
    HttpResponse::Ok().json(results)
}

#[get("/assignments/{id}/export")]
pub async fn export_submissions_csv(path: web::Path<String>) -> HttpResponse {
    let db = get_db();
    let col = db.collection::<mongodb::bson::Document>("submissions");
    let id = match ObjectId::parse_str(path.into_inner()) {
        Ok(id) => id,
        Err(_) => return HttpResponse::BadRequest().finish(),
    };
    let filter = doc! { "assignment_id": id };
    let mut csv = String::from("roll_no,file_name,submitted_at,status\n");
    if let Ok(mut cur) = col.find(filter, None).await {
        while let Ok(Some(doc)) = cur.try_next().await {
            let roll = doc.get_str("roll_no").unwrap_or("");
            let path_str = doc.get_str("file_path").unwrap_or("");
            let fname = std::path::Path::new(path_str).file_name()
                .and_then(|n| n.to_str()).unwrap_or(path_str);
            let submitted_at = doc.get_str("submitted_at").unwrap_or("");
            let status = doc.get_str("status").unwrap_or("submitted");
            csv.push_str(&format!("{},{},{},{}\n", roll, fname, submitted_at, status));
        }
    }
    HttpResponse::Ok()
        .append_header(("Content-Type", "text/csv"))
        .append_header(("Content-Disposition", "attachment; filename=\"submissions.csv\""))
        .body(csv)
}

#[get("/submissions/status")]
pub async fn get_submission_status(query: web::Query<std::collections::HashMap<String, String>>) -> HttpResponse {
    let db = get_db();
    let col = db.collection::<mongodb::bson::Document>("submissions");
    let assignment_id_str = query.get("assignment_id").cloned().unwrap_or_default();
    let student_id_str = query.get("student_id").cloned().unwrap_or_default();

    let assignment_id = match ObjectId::parse_str(&assignment_id_str) {
        Ok(id) => id,
        Err(_) => return HttpResponse::NotFound().finish(),
    };
    let student_id = match ObjectId::parse_str(&student_id_str) {
        Ok(id) => id,
        Err(_) => return HttpResponse::NotFound().finish(),
    };

    match col.find_one(doc! { "assignment_id": assignment_id, "student_id": student_id }, None).await {
        Ok(Some(doc)) => {
            let path_str = doc.get_str("file_path").unwrap_or("");
            let fname = std::path::Path::new(path_str).file_name()
                .and_then(|n| n.to_str()).unwrap_or(path_str);
            HttpResponse::Ok().json(serde_json::json!({
                "assignment_id": assignment_id_str,
                "file_name": fname,
                "submitted_at": doc.get_str("submitted_at").unwrap_or(""),
                "status": doc.get_str("status").unwrap_or("submitted"),
            }))
        }
        _ => HttpResponse::NotFound().finish(),
    }
}

// GET /submissions/{id}/download — serve the submitted file to staff
#[get("/submissions/{id}/download")]
pub async fn download_submission(path: web::Path<String>) -> HttpResponse {
    let db = get_db();
    let col = db.collection::<mongodb::bson::Document>("submissions");
    let id = match ObjectId::parse_str(path.into_inner()) {
        Ok(id) => id,
        Err(_) => return HttpResponse::BadRequest().finish(),
    };
    if let Ok(Some(doc)) = col.find_one(doc! { "_id": id }, None).await {
        if let Ok(path_str) = doc.get_str("file_path") {
            if let Ok(data) = std::fs::read(path_str) {
                let fname = std::path::Path::new(path_str)
                    .file_name()
                    .and_then(|n| n.to_str())
                    .unwrap_or("submission");
                return HttpResponse::Ok()
                    .append_header(("Content-Disposition", format!("attachment; filename=\"{}\"", fname)))
                    .body(data);
            }
        }
    }
    HttpResponse::NotFound().json(serde_json::json!({ "error": "File not found" }))
}

