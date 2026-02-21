use std::process::Command;

fn main() {
    // Tell Cargo to re-run this build script if any frontend source file changes
    println!("cargo:rerun-if-changed=webpage/src");
    println!("cargo:rerun-if-changed=webpage/index.html");
    println!("cargo:rerun-if-changed=webpage/package.json");

    println!("cargo:warning=Building frontend with npm...");

    let output = Command::new("cmd")
        .args(["/C", "cd webpage && npm run build"])
        .output()
        .expect("Failed to run npm run build");

    if !output.status.success() {
        let stderr = String::from_utf8_lossy(&output.stderr);
        let stdout = String::from_utf8_lossy(&output.stdout);
        panic!(
            "npm run build failed!\nSTDOUT:\n{}\nSTDERR:\n{}",
            stdout, stderr
        );
    }

    // Copy dist → build so rust-embed picks it up
    let src = std::path::Path::new("webpage/dist");
    let dst = std::path::Path::new("webpage/build");

    if dst.exists() {
        std::fs::remove_dir_all(dst).ok();
    }
    copy_dir_all(src, dst).expect("Failed to copy dist to build");

    println!("cargo:warning=Frontend build complete.");
}

fn copy_dir_all(src: &std::path::Path, dst: &std::path::Path) -> std::io::Result<()> {
    std::fs::create_dir_all(dst)?;
    for entry in std::fs::read_dir(src)? {
        let entry = entry?;
        let ty = entry.file_type()?;
        if ty.is_dir() {
            copy_dir_all(&entry.path(), &dst.join(entry.file_name()))?;
        } else {
            std::fs::copy(entry.path(), dst.join(entry.file_name()))?;
        }
    }
    Ok(())
}
