use actix_web::{post, web, HttpResponse};
use mongodb::bson::{doc, DateTime as BsonDateTime};
use serde::{Deserialize, Serialize};
use crate::lab_module::mongo_connection::get_db;

#[derive(Debug, Deserialize)]
pub struct LoginRequest {
    pub identifier: String,  // roll_no for student, email or name for teacher
    pub password: String,
    pub expected_role: String, // "student" or "teacher"
}

#[derive(Debug, Serialize)]
pub struct LoginResponse {
    pub id: String,
    pub name: String,
    pub role: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub roll_no: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub email: Option<String>,
}

#[post("/auth/login")]
pub async fn login(body: web::Json<LoginRequest>) -> HttpResponse {
    let db = get_db();
    let users = db.collection::<mongodb::bson::Document>("users");

    let filter = if body.expected_role == "student" {
        doc! {
            "roll_no": &body.identifier,
            "password": &body.password,
            "role": "student",
            "is_active": true
        }
    } else {
        doc! {
            "$or": [
                { "email": &body.identifier },
                { "name": &body.identifier }
            ],
            "password": &body.password,
            "role": "teacher",
            "is_active": true
        }
    };

    match users.find_one(filter, None).await {
        Ok(Some(doc)) => {
            let id = doc.get_object_id("_id").map(|id| id.to_hex()).unwrap_or_default();
            let name = doc.get_str("name").unwrap_or("").to_string();
            let role = doc.get_str("role").unwrap_or("").to_string();
            let roll_no = doc.get_str("roll_no").ok().map(|s| s.to_string());
            let email = doc.get_str("email").ok().map(|s| s.to_string());

            HttpResponse::Ok().json(LoginResponse { id, name, role, roll_no, email })
        }
        Ok(None) => {
            HttpResponse::Unauthorized().json(serde_json::json!({ "error": "Invalid credentials or account inactive." }))
        }
        Err(e) => {
            eprintln!("LAB AUTH: DB error: {}", e);
            HttpResponse::InternalServerError().json(serde_json::json!({ "error": "Database error." }))
        }
    }
}
