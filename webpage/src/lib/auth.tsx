import React, { createContext, useContext, useState, useCallback } from "react";

interface AuthUser {
    id: string;
    name: string;
    role: "staff" | "student";
    roll_no?: string;
}

interface AuthContextType {
    user: AuthUser | null;
    setUser: (u: AuthUser | null) => void;
    logout: () => void;
}

const AuthContext = createContext<AuthContextType>({
    user: null,
    setUser: () => { },
    logout: () => { },
});

export function AuthProvider({ children }: { children: React.ReactNode }) {
    const [user, setUser] = useState<AuthUser | null>(() => {
        try {
            const stored = sessionStorage.getItem("edunet_user");
            return stored ? JSON.parse(stored) : null;
        } catch { return null; }
    });

    const handleSetUser = useCallback((u: AuthUser | null) => {
        setUser(u);
        if (u) sessionStorage.setItem("edunet_user", JSON.stringify(u));
        else sessionStorage.removeItem("edunet_user");
    }, []);

    const logout = useCallback(() => handleSetUser(null), [handleSetUser]);

    return (
        <AuthContext.Provider value={{ user, setUser: handleSetUser, logout }}>
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth() {
    return useContext(AuthContext);
}
