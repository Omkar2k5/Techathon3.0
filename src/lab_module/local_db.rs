use lazy_static::lazy_static;
use rusqlite::{params, Connection, Result, OptionalExtension};
use std::sync::Mutex;

lazy_static! {
    static ref LOCAL_DB: Mutex<Connection> = {
        let conn = Connection::open("local.db").expect("Failed to open local SQLite db");
        // Create table for local user auth cache
        conn.execute(
            "CREATE TABLE IF NOT EXISTS local_users (
                id TEXT PRIMARY KEY,
                identifier TEXT NOT NULL,
                password TEXT NOT NULL,
                role TEXT NOT NULL,
                name TEXT NOT NULL,
                roll_no TEXT,
                email TEXT
            )",
            [],
        ).expect("Failed to create local_users table");
        Mutex::new(conn)
    };
}

// Ensure the lazy static is initialized once on startup
pub fn init_local_db() {
    drop(LOCAL_DB.lock().unwrap());
    println!("LAB: Local SQLite db initialized at local.db");
}

pub fn cache_user(
    id: &str,
    identifier: &str,
    password: &str,
    role: &str,
    name: &str,
    roll_no: Option<&str>,
    email: Option<&str>,
) -> Result<()> {
    let conn = LOCAL_DB.lock().unwrap();
    conn.execute(
        "INSERT INTO local_users (id, identifier, password, role, name, roll_no, email)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)
         ON CONFLICT(id) DO UPDATE SET
            identifier=excluded.identifier,
            password=excluded.password,
            role=excluded.role,
            name=excluded.name,
            roll_no=excluded.roll_no,
            email=excluded.email",
        params![id, identifier, password, role, name, roll_no, email],
    )?;
    Ok(())
}

pub fn verify_offline_login(
    identifier: &str,
    password: &str,
    expected_role: &str,
) -> Result<Option<(String, String, Option<String>, Option<String>)>> {
    let conn = LOCAL_DB.lock().unwrap();
    let mut stmt = conn.prepare(
        "SELECT id, name, roll_no, email FROM local_users WHERE identifier = ?1 AND password = ?2 AND role = ?3",
    )?;
    
    let result = stmt.query_row(params![identifier, password, expected_role], |row| {
        Ok((
            row.get::<_, String>(0)?,
            row.get::<_, String>(1)?,
            row.get::<_, Option<String>>(2)?,
            row.get::<_, Option<String>>(3)?,
        ))
    }).optional()?;
    
    if expected_role == "teacher" && result.is_none() {
        // Teachers could login with email OR name. Fallback query for just name if it acts as the identifier.
        let mut alt_stmt = conn.prepare(
            "SELECT id, name, roll_no, email FROM local_users WHERE (email = ?1 OR name = ?1) AND password = ?2 AND role = ?3"
        )?;
        let alt_res = alt_stmt.query_row(params![identifier, password, expected_role], |row| {
            Ok((
                row.get::<_, String>(0)?,
                row.get::<_, String>(1)?,
                row.get::<_, Option<String>>(2)?,
                row.get::<_, Option<String>>(3)?,
            ))
        }).optional()?;
        return Ok(alt_res);
    }

    Ok(result)
}
