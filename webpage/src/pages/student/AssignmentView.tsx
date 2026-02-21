import { useState, useEffect, useRef } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import {
  ArrowLeft, Upload, Download, CheckCircle, Clock,
  AlertCircle, Loader2, FileCode, GraduationCap
} from "lucide-react";
import { useAuth } from "@/lib/auth";
import {
  getActiveAssignments, uploadFile, getSubmissionStatus,
  sampleFileUrl, type ActiveAssignment
} from "@/lib/api";
import { useToast } from "@/hooks/use-toast";

function Countdown({ deadline }: { deadline: string }) {
  const [label, setLabel] = useState("");
  const [pct, setPct] = useState(100);
  const [expired, setExpired] = useState(false);

  useEffect(() => {
    const mountTime = Date.now();
    const end = new Date(deadline).getTime();
    const total = Math.max(1, end - mountTime);

    const update = () => {
      const diff = end - Date.now();
      if (diff <= 0) {
        setLabel("Time's up");
        setPct(0);
        setExpired(true);
        return;
      }
      const h = Math.floor(diff / 3600000);
      const m = Math.floor((diff % 3600000) / 60000);
      const s = Math.floor((diff % 60000) / 1000);
      setLabel(h > 0 ? `${h}h ${m}m ${s}s remaining` : `${m}m ${s}s remaining`);
      setPct(Math.max(0, (diff / total) * 100));
    };
    update();
    const t = setInterval(update, 1000);
    return () => clearInterval(t);
  }, [deadline]);

  const barColor = expired ? "bg-destructive" : pct > 40 ? "bg-primary" : pct > 15 ? "bg-orange-500" : "bg-red-500";
  const textColor = expired ? "text-destructive" : pct > 40 ? "text-foreground" : pct > 15 ? "text-orange-700" : "text-red-700";

  return (
    <div className="space-y-2">
      <div className={`flex items-center gap-2 text-sm font-semibold ${textColor}`}>
        <Clock className="w-4 h-4" />
        {label}
      </div>
      <div className="w-full h-2 bg-secondary rounded-full overflow-hidden">
        <div className={`h-full rounded-full transition-[width] duration-1000 ${barColor}`}
          style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}

const AssignmentView = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const { toast } = useToast();

  const [assignment, setAssignment] = useState<ActiveAssignment | null>(null);
  const [loadingAssignment, setLoadingAssignment] = useState(true);
  const [submitted, setSubmitted] = useState(false);
  const [submissionInfo, setSubmissionInfo] = useState<any>(null);
  const [uploading, setUploading] = useState(false);
  const [dragOver, setDragOver] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (user && id) {
      fetchAssignment();
      checkSubmission();
    }
  }, [user, id]);

  const fetchAssignment = async () => {
    setLoadingAssignment(true);
    const all = await getActiveAssignments(user!.id);
    setAssignment(all.find(a => a.id === id) ?? null);
    setLoadingAssignment(false);
  };

  const checkSubmission = async () => {
    const status = await getSubmissionStatus(id!, user!.id);
    if (status) { setSubmitted(true); setSubmissionInfo(status); }
  };

  const handleUpload = async (file: File) => {
    const ext = "." + file.name.split(".").pop()!.toLowerCase();
    if (assignment && !assignment.allowed_file_types.includes(ext)) {
      toast({
        title: "Wrong file type",
        description: `Only ${assignment.allowed_file_types.join(", ")} files are accepted.`,
        variant: "destructive",
      });
      return;
    }
    setUploading(true);
    try {
      const fd = new FormData();
      fd.append("file", file);
      fd.append("assignment_id", id!);
      fd.append("subject_id", assignment!.subject_id);
      fd.append("student_id", user!.id);
      fd.append("roll_no", user!.roll_no ?? user!.id);
      await uploadFile(fd);
      setSubmitted(true);
      await checkSubmission();
      toast({ title: "✓ Submitted!", description: `${file.name} uploaded and recorded.` });
    } catch (err: any) {
      toast({ title: "Upload failed", description: err.message, variant: "destructive" });
    } finally { setUploading(false); }
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setDragOver(false);
    const file = e.dataTransfer.files[0];
    if (file) handleUpload(file);
  };

  if (loadingAssignment) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center">
        <div className="text-center space-y-3">
          <Loader2 className="w-8 h-8 animate-spin text-muted-foreground mx-auto" />
          <p className="text-sm text-muted-foreground">Loading assignment...</p>
        </div>
      </div>
    );
  }

  if (!assignment) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center">
        <div className="text-center space-y-4">
          <AlertCircle className="w-10 h-10 text-muted-foreground mx-auto" />
          <p className="text-sm font-medium">Assignment not found or no longer active</p>
          <Button variant="outline" size="sm" onClick={() => navigate("/student/dashboard")}>
            <ArrowLeft className="w-4 h-4 mr-2" />Back to Dashboard
          </Button>
        </div>
      </div>
    );
  }

  const isExpired = new Date(assignment.deadline) < new Date();

  return (
    <div className="min-h-screen bg-background">
      {/* Header */}
      <header className="border-b border-border bg-card sticky top-0 z-10">
        <div className="container flex items-center gap-4 h-16 px-6">
          <Button variant="ghost" size="icon" onClick={() => navigate("/student/dashboard")}>
            <ArrowLeft className="w-4 h-4" />
          </Button>
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <h1 className="text-sm font-bold truncate">{assignment.assignment_name}</h1>
              <span className="font-mono text-xs px-2 py-0.5 bg-secondary rounded shrink-0">
                {assignment.subject_code}
              </span>
              {!isExpired && !submitted && (
                <Badge className="bg-primary text-primary-foreground gap-1 text-xs">
                  <span className="w-1.5 h-1.5 bg-primary-foreground rounded-full animate-pulse" />
                  LIVE
                </Badge>
              )}
              {submitted && (
                <Badge className="bg-green-600 text-white text-xs gap-1">
                  <CheckCircle className="w-3 h-3" />Submitted
                </Badge>
              )}
            </div>
            <p className="text-xs text-muted-foreground">{assignment.subject_name}</p>
          </div>
          {/* Student ID */}
          <div className="flex items-center gap-2 shrink-0 text-xs text-muted-foreground">
            <GraduationCap className="w-3.5 h-3.5" />
            <span className="font-mono">{user?.roll_no ?? user?.name}</span>
          </div>
        </div>
      </header>

      <main className="container max-w-2xl px-6 py-8 space-y-5">
        {/* Timer card */}
        <Card className="border-border">
          <CardContent className="p-6 space-y-4">
            <div className="flex items-start justify-between">
              <div>
                <p className="text-sm font-semibold">{assignment.subject_name}</p>
                <p className="text-xs text-muted-foreground mt-0.5">
                  {assignment.time_limit_minutes} min session · ends {new Date(assignment.deadline).toLocaleString()}
                </p>
              </div>
              <div className="flex items-center gap-2">
                {assignment.allowed_file_types.map(ft => (
                  <span key={ft} className="font-mono text-xs bg-secondary px-2 py-0.5 rounded">{ft}</span>
                ))}
              </div>
            </div>
            {submitted ? (
              <div className="flex items-center gap-2 text-sm font-semibold text-green-700">
                <CheckCircle className="w-4 h-4" /> Submitted successfully
              </div>
            ) : (
              <Countdown deadline={assignment.deadline} />
            )}
          </CardContent>
        </Card>

        {/* Submission confirmed */}
        {submitted && submissionInfo && (
          <Card className="border-green-200 bg-green-50">
            <CardContent className="p-5">
              <div className="flex items-start gap-3">
                <div className="w-8 h-8 rounded-full bg-green-600 flex items-center justify-center shrink-0">
                  <CheckCircle className="w-4 h-4 text-white" />
                </div>
                <div>
                  <p className="text-sm font-semibold text-green-800">Submission Confirmed</p>
                  <p className="text-xs text-green-700 mt-0.5 font-mono">
                    {submissionInfo.file_path?.split("/").pop() ?? submissionInfo.file_name ?? "file uploaded"}
                  </p>
                  <p className="text-xs text-green-600 mt-0.5">
                    Recorded at {new Date(submissionInfo.submitted_at).toLocaleString()}
                  </p>
                </div>
              </div>
            </CardContent>
          </Card>
        )}

        {/* Sample download */}
        {assignment.has_sample && (
          <div className="flex items-center gap-3">
            <Button variant="outline" className="gap-2"
              onClick={() => window.open(sampleFileUrl(id!), "_blank")}>
              <Download className="w-4 h-4" />
              Download Sample File
            </Button>
            <p className="text-xs text-muted-foreground">Template provided by your instructor</p>
          </div>
        )}

        {/* Upload zone — only show if not submitted and not expired */}
        {!submitted && !isExpired && (
          <div>
            <input ref={fileRef} type="file" className="hidden"
              onChange={e => { const f = e.target.files?.[0]; if (f) handleUpload(f); e.target.value = ""; }} />
            <div
              className={`border-2 border-dashed rounded-xl p-10 text-center transition-all cursor-pointer ${dragOver
                ? "border-primary bg-primary/5"
                : "border-border hover:border-foreground/30 hover:bg-secondary/30"
                }`}
              onClick={() => !uploading && fileRef.current?.click()}
              onDragOver={e => { e.preventDefault(); setDragOver(true); }}
              onDragLeave={() => setDragOver(false)}
              onDrop={handleDrop}
            >
              {uploading ? (
                <div className="space-y-3">
                  <Loader2 className="w-10 h-10 mx-auto text-muted-foreground animate-spin" />
                  <p className="text-sm font-medium text-foreground">Uploading...</p>
                </div>
              ) : (
                <div className="space-y-3">
                  <div className="w-12 h-12 rounded-xl bg-secondary flex items-center justify-center mx-auto">
                    <Upload className="w-6 h-6 text-muted-foreground" />
                  </div>
                  <div>
                    <p className="text-sm font-semibold text-foreground">Drop your solution here</p>
                    <p className="text-xs text-muted-foreground mt-1">
                      or click to browse · accepts {assignment.allowed_file_types.join(", ")}
                    </p>
                  </div>
                  <Button size="sm" className="gap-2 mt-2">
                    <FileCode className="w-4 h-4" />
                    Choose File
                  </Button>
                </div>
              )}
            </div>
          </div>
        )}

        {/* Deadline expired warning */}
        {isExpired && !submitted && (
          <Card className="border-destructive/30 bg-destructive/5">
            <CardContent className="p-5">
              <div className="flex items-center gap-3 text-destructive">
                <AlertCircle className="w-5 h-5 shrink-0" />
                <div>
                  <p className="text-sm font-semibold">Submission Closed</p>
                  <p className="text-xs mt-0.5">The deadline has passed. Contact your instructor if you need assistance.</p>
                </div>
              </div>
            </CardContent>
          </Card>
        )}

        {/* Instructions footer */}
        <div className="text-xs text-muted-foreground border-t border-border pt-4 space-y-1">
          <p>• Submit only {assignment.allowed_file_types.join(" or ")} files</p>
          <p>• You may re-upload before the deadline — only the latest submission is kept</p>
          <p>• All submissions are timestamped and tied to your roll number</p>
        </div>
      </main>
    </div>
  );
};

export default AssignmentView;
