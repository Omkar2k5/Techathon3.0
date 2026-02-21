pub mod mongo_connection;
pub mod role_auth;
pub mod subject_manager;
pub mod assignment_manager;
pub mod submission_manager;
pub mod file_transfer;
pub mod scheduler;
pub mod chat_handler;

use actix_web::web;

pub fn config(cfg: &mut web::ServiceConfig) {
    cfg
        .service(role_auth::login)
        .service(subject_manager::get_teacher_subjects)
        .service(subject_manager::get_student_subjects)
        .service(subject_manager::create_subject)
        .service(assignment_manager::create_assignment)
        .service(assignment_manager::start_assignment)
        .service(assignment_manager::close_assignment)
        .service(assignment_manager::get_active_assignments)
        .service(assignment_manager::get_subject_assignments)
        .service(assignment_manager::download_sample)
        .service(submission_manager::get_submissions)
        .service(submission_manager::export_submissions_csv)
        .service(submission_manager::get_submission_status)
        .service(file_transfer::upload_file)
        .service(chat_handler::lab_chat);
}
