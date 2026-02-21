import { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { ArrowLeft, Download, FileCode, Loader2, BookOpen } from "lucide-react";
import { useAuth } from "@/lib/auth";
import {
    getSubjectAssignments, getSubmissions, submissionDownloadUrl,
    getStudentSubjects, type Subject, type Submission
} from "@/lib/api";

const StudentSubjectHistory = () => {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const { user } = useAuth();
    const [subject, setSubject] = useState<Subject | null>(null);
    const [submissions, setSubmissions] = useState<(Submission & { assignment_name: string })[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        if (!user || !id) return;
        const load = async () => {
            const subjects = await getStudentSubjects(user.id);
            const found = subjects.find(s => s.id === id) ?? null;
            setSubject(found);
            if (found) {
                const assignments = await getSubjectAssignments(found.id);
                const allSubs: (Submission & { assignment_name: string })[] = [];
                for (const a of assignments) {
                    const subs = await getSubmissions(a.id);
                    const mine = subs.filter(s => s.roll_no === user.roll_no || s.roll_no === user.id);
                    mine.forEach(s => allSubs.push({ ...s, assignment_name: a.assignment_name }));
                }
                // Sort newest first
                allSubs.sort((a, b) => new Date(b.submitted_at).getTime() - new Date(a.submitted_at).getTime());
                setSubmissions(allSubs);
            }
            setLoading(false);
        };
        load();
    }, [user, id]);

    return (
        <div className="min-h-screen bg-background">
            <header className="border-b border-border bg-card sticky top-0 z-10">
                <div className="container flex items-center gap-4 h-16 px-6">
                    <Button variant="ghost" size="icon" onClick={() => navigate("/student/dashboard")}>
                        <ArrowLeft className="w-4 h-4" />
                    </Button>
                    <div>
                        <h1 className="text-sm font-bold">{subject?.subject_name ?? "Loading..."}</h1>
                        <p className="text-xs text-muted-foreground font-mono">{subject?.subject_code} · Your submission history</p>
                    </div>
                </div>
            </header>

            <main className="container px-6 py-8 max-w-2xl mx-auto">
                {loading ? (
                    <div className="flex items-center justify-center py-20">
                        <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
                    </div>
                ) : submissions.length === 0 ? (
                    <div className="text-center py-20 text-muted-foreground">
                        <BookOpen className="w-12 h-12 mx-auto mb-4 opacity-20" />
                        <p className="text-sm font-medium">No submissions yet</p>
                        <p className="text-xs mt-2">When you submit a lab for this subject, it will appear here.</p>
                    </div>
                ) : (
                    <div className="space-y-3">
                        {submissions.map((sub) => (
                            <Card key={sub.id} className="border-border">
                                <CardContent className="p-4 flex items-center justify-between gap-4">
                                    <div className="flex items-center gap-3 min-w-0">
                                        <FileCode className="w-8 h-8 text-primary shrink-0" />
                                        <div className="min-w-0">
                                            <p className="text-sm font-semibold">{sub.assignment_name}</p>
                                            <p className="text-xs text-muted-foreground font-mono truncate">{sub.file_path.split("/").pop()}</p>
                                            <p className="text-xs text-muted-foreground">{new Date(sub.submitted_at).toLocaleString()}</p>
                                        </div>
                                    </div>
                                    <a
                                        href={submissionDownloadUrl(sub.id)}
                                        download
                                        className="p-2 rounded-lg hover:bg-secondary text-muted-foreground hover:text-foreground transition-colors shrink-0"
                                        title="Download your submission"
                                    >
                                        <Download className="w-5 h-5" />
                                    </a>
                                </CardContent>
                            </Card>
                        ))}
                    </div>
                )}
            </main>
        </div>
    );
};

export default StudentSubjectHistory;
