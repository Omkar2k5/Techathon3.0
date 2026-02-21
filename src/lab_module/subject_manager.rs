use actix_web::{get, post, delete, web, HttpResponse};
use mongodb::bson::{doc, oid::ObjectId};
use serde::{Deserialize, Serialize};
use chrono::Utc;
use futures::TryStreamExt;
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

#[derive(Debug, Deserialize)]
pub struct JoinSubjectRequest {
    pub student_id: String,
    pub subject_code: String,
}

#[derive(Debug, Deserialize)]
pub struct EnrollStudentRequest {
    pub roll_no: String,
}

// GET /subjects/teacher/{teacher_id}
#[get("/subjects/teacher/{teacher_id}")]
pub async fn get_teacher_subjects(path: web::Path<String>) -> HttpResponse {
    let db = get_db();
    let col = db.collection::<mongodb::bson::Document>("subjects");
    let teacher_id = match ObjectId::parse_str(path.into_inner()) {
        Ok(id) => id,
        Err(_) => return HttpResponse::BadRequest().json(serde_json::json!({"error":"Invalid teacher ID"})),
    };

    // Get all active assignments to determine which subjects have live labs
    let assignments_col = db.collection::<mongodb::bson::Document>("assignments");
    let mut active_subject_ids = std::collections::HashSet::new();
    if let Ok(mut cur) = assignments_col.find(doc! { "is_active": true }, None).await {
        while let Ok(Some(doc)) = cur.try_next().await {
            if let Ok(sid) = doc.get_object_id("subject_id") {
                active_subject_ids.insert(sid.to_hex());
            }
        }
    }

    let filter = doc! { "teacher_id": teacher_id, "is_active": true };
    match col.find(filter, None).await {
        Ok(mut cursor) => {
            let mut results = vec![];
            while let Ok(Some(doc)) = cursor.try_next().await {
                let id = doc.get_object_id("_id").map(|id| id.to_hex()).unwrap_or_default();
                let has_active_lab = active_subject_ids.contains(&id);

                // Count enrolled students
                let enrollments_col = db.collection::<mongodb::bson::Document>("subject_enrollments");
                let subject_oid = doc.get_object_id("_id").ok();
                let student_count = if let Some(sid) = subject_oid {
                    enrollments_col.count_documents(doc! { "subject_id": sid }, None).await.unwrap_or(0)
                } else { 0 };

                results.push(serde_json::json!({
                    "id": id,
                    "subject_name": doc.get_str("subject_name").unwrap_or(""),
                    "subject_code": doc.get_str("subject_code").unwrap_or(""),
                    "has_active_lab": has_active_lab,
                    "student_count": student_count,
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

// GET /subjects/student/{student_id}
#[get("/subjects/student/{student_id}")]
pub async fn get_student_subjects(path: web::Path<String>) -> HttpResponse {
    let db = get_db();
    let enrollments = db.collection::<mongodb::bson::Document>("subject_enrollments");
    let subjects_col = db.collection::<mongodb::bson::Document>("subjects");
    let users_col = db.collection::<mongodb::bson::Document>("users");
    let student_id = match ObjectId::parse_str(path.into_inner()) {
        Ok(id) => id,
        Err(_) => return HttpResponse::BadRequest().json(serde_json::json!({"error":"Invalid student ID"})),
    };

    let filter = doc! { "student_id": student_id };
    let mut subject_ids = vec![];
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

            // Get teacher name
            let mut teacher_name = String::new();
            if let Ok(tid) = doc.get_object_id("teacher_id") {
                if let Ok(Some(udoc)) = users_col.find_one(doc! { "_id": tid }, None).await {
                    teacher_name = udoc.get_str("name").unwrap_or("").to_string();
                }
            }

            results.push(serde_json::json!({
                "id": id,
                "subject_name": doc.get_str("subject_name").unwrap_or(""),
                "subject_code": doc.get_str("subject_code").unwrap_or(""),
                "teacher_name": teacher_name,
            }));
        }
    }
    HttpResponse::Ok().json(results)
}

// POST /subjects/create
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

// POST /subjects/join  — student joins by subject code
#[post("/subjects/join")]
pub async fn join_subject(body: web::Json<JoinSubjectRequest>) -> HttpResponse {
    let db = get_db();
    let subjects_col = db.collection::<mongodb::bson::Document>("subjects");
    let enrollments_col = db.collection::<mongodb::bson::Document>("subject_enrollments");

    let student_id = match ObjectId::parse_str(&body.student_id) {
        Ok(id) => id,
        Err(_) => return HttpResponse::BadRequest().json(serde_json::json!({"error":"Invalid student ID"})),
    };

    // Find subject by code
    let subject = match subjects_col.find_one(
        doc! { "subject_code": &body.subject_code, "is_active": true }, None
    ).await {
        Ok(Some(s)) => s,
        _ => return HttpResponse::NotFound().json(serde_json::json!({"error":"Subject code not found"})),
    };

    let subject_id = subject.get_object_id("_id").unwrap();

    // Check if already enrolled
    let already = enrollments_col.find_one(
        doc! { "student_id": student_id, "subject_id": subject_id }, None
    ).await.ok().flatten();

    if already.is_some() {
        return HttpResponse::Conflict().json(serde_json::json!({"error":"Already enrolled in this subject"}));
    }

    let enrollment = doc! {
        "student_id": student_id,
        "subject_id": subject_id,
        "enrolled_at": Utc::now().to_rfc3339(),
    };
    match enrollments_col.insert_one(enrollment, None).await {
        Ok(_) => HttpResponse::Ok().json(serde_json::json!({
            "message": "Enrolled successfully",
            "subject_name": subject.get_str("subject_name").unwrap_or(""),
            "subject_code": subject.get_str("subject_code").unwrap_or(""),
        })),
        Err(e) => HttpResponse::InternalServerError().json(serde_json::json!({"error": e.to_string()})),
    }
}

// GET /subjects/{id}/students
#[get("/subjects/{id}/students")]
pub async fn get_subject_students(path: web::Path<String>) -> HttpResponse {
    let db = get_db();
    let enrollments_col = db.collection::<mongodb::bson::Document>("subject_enrollments");
    let users_col = db.collection::<mongodb::bson::Document>("users");

    let subject_id = match ObjectId::parse_str(path.into_inner()) {
        Ok(id) => id,
        Err(_) => return HttpResponse::BadRequest().json(serde_json::json!({"error":"Invalid subject ID"})),
    };

    let mut results = vec![];
    if let Ok(mut cur) = enrollments_col.find(doc! { "subject_id": subject_id }, None).await {
        while let Ok(Some(edoc)) = cur.try_next().await {
            if let Ok(sid) = edoc.get_object_id("student_id") {
                if let Ok(Some(udoc)) = users_col.find_one(doc! { "_id": sid }, None).await {
                    results.push(serde_json::json!({
                        "id": sid.to_hex(),
                        "name": udoc.get_str("name").unwrap_or(""),
                        "roll_no": udoc.get_str("roll_no").unwrap_or(""),
                        "email": udoc.get_str("email").unwrap_or(""),
                    }));
                }
            }
        }
    }
    HttpResponse::Ok().json(results)
}

// POST /subjects/{id}/enroll  — teacher adds student by roll_no
#[post("/subjects/{id}/enroll")]
pub async fn enroll_student(path: web::Path<String>, body: web::Json<EnrollStudentRequest>) -> HttpResponse {
    let db = get_db();
    let users_col = db.collection::<mongodb::bson::Document>("users");
    let enrollments_col = db.collection::<mongodb::bson::Document>("subject_enrollments");

    let subject_id = match ObjectId::parse_str(path.into_inner()) {
        Ok(id) => id,
        Err(_) => return HttpResponse::BadRequest().json(serde_json::json!({"error":"Invalid subject ID"})),
    };

    // Find student by roll_no
    let user = match users_col.find_one(doc! { "roll_no": &body.roll_no }, None).await {
        Ok(Some(u)) => u,
        _ => return HttpResponse::NotFound().json(serde_json::json!({"error":"Student with that roll number not found"})),
    };

    let student_id = user.get_object_id("_id").unwrap();

    // Already enrolled?
    if let Ok(Some(_)) = enrollments_col.find_one(
        doc! { "student_id": student_id, "subject_id": subject_id }, None
    ).await {
        return HttpResponse::Conflict().json(serde_json::json!({"error":"Already enrolled"}));
    }

    match enrollments_col.insert_one(doc! {
        "student_id": student_id,
        "subject_id": subject_id,
        "enrolled_at": Utc::now().to_rfc3339(),
    }, None).await {
        Ok(_) => HttpResponse::Ok().json(serde_json::json!({
            "message": "Student enrolled",
            "student_id": student_id.to_hex(),
            "name": user.get_str("name").unwrap_or(""),
        })),
        Err(e) => HttpResponse::InternalServerError().json(serde_json::json!({"error": e.to_string()})),
    }
}

// DELETE /subjects/{id}/students/{student_id}
#[delete("/subjects/{subject_id}/students/{student_id}")]
pub async fn remove_student(path: web::Path<(String, String)>) -> HttpResponse {
    let db = get_db();
    let enrollments_col = db.collection::<mongodb::bson::Document>("subject_enrollments");
    let (subject_id_str, student_id_str) = path.into_inner();

    let subject_id = match ObjectId::parse_str(&subject_id_str) {
        Ok(id) => id,
        Err(_) => return HttpResponse::BadRequest().json(serde_json::json!({"error":"Invalid subject ID"})),
    };
    let student_id = match ObjectId::parse_str(&student_id_str) {
        Ok(id) => id,
        Err(_) => return HttpResponse::BadRequest().json(serde_json::json!({"error":"Invalid student ID"})),
    };

    match enrollments_col.delete_one(
        doc! { "subject_id": subject_id, "student_id": student_id }, None
    ).await {
        Ok(_) => HttpResponse::Ok().json(serde_json::json!({"message":"Student removed"})),
        Err(e) => HttpResponse::InternalServerError().json(serde_json::json!({"error": e.to_string()})),
    }
}
