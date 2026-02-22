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
    let filter = if body.expected_role == "student" {
        doc! {
            "roll_no": &body.identifier,
            "password": &body.password,
            "role": "student"
        }
    } else {
        doc! {
            "$or": [
                { "email": &body.identifier },
                { "name": &body.identifier }
            ],
            "password": &body.password,
            "role": "teacher"
        }
    };

    // Attempt online login with a 3 second timeout
    let db = get_db();
    let users = db.collection::<mongodb::bson::Document>("users");
    
    let online_result = tokio::time::timeout(
        std::time::Duration::from_secs(3),
        users.find_one(filter, None)
    ).await;

    match online_result {
        Ok(Ok(Some(doc))) => {
            let id = doc.get_object_id("_id").map(|id| id.to_hex()).unwrap_or_default();
            let name = doc.get_str("name").unwrap_or("").to_string();
            let role = doc.get_str("role").unwrap_or("").to_string();
            let roll_no = doc.get_str("roll_no").ok().map(|s| s.to_string());
            let email = doc.get_str("email").ok().map(|s| s.to_string());

            // Cache the user for offline access
            let roll_ref = roll_no.as_deref();
            let email_ref = email.as_deref();
            if let Err(e) = crate::lab_module::local_db::cache_user(
                &id, &body.identifier, &body.password, &role, &name, roll_ref, email_ref,
            ) {
                eprintln!("LAB AUTH: Failed to cache user for offline mode: {}", e);
            }

            HttpResponse::Ok().json(LoginResponse { id, name, role, roll_no, email })
        }
        Ok(Ok(None)) => {
            HttpResponse::Unauthorized().json(serde_json::json!({ "error": "Invalid credentials or account inactive." }))
        }
        Ok(Err(_)) | Err(_) => {
            // Mongo error or timeout (system offline/unreachable)
            // Fallback to offline local check
            println!("LAB AUTH: Network error or timeout attempting MongoDB. Falling back to local offline auth.");
            match crate::lab_module::local_db::verify_offline_login(&body.identifier, &body.password, &body.expected_role) {
                Ok(Some((id, name, roll_no, email))) => {
                     println!("LAB AUTH: Successful offline login for {}", body.identifier);
                     HttpResponse::Ok().json(LoginResponse { id, name, role: body.expected_role.clone(), roll_no, email })
                },
                Ok(None) => HttpResponse::Unauthorized().json(serde_json::json!({ "error": "Invalid credentials or account inactive." })),
                Err(sqlite_err) => {
                    eprintln!("LAB AUTH: Local DB error on fallback: {}", sqlite_err);
                    HttpResponse::InternalServerError().json(serde_json::json!({ "error": "Authentication currently unavailable (Offline cache error)." }))
                }
            }
        }
    }
}
