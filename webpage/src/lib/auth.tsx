import React, { createContext, useContext, useState, useCallback } from "react";

interface AuthUser {
    id: string;
    name: string;
    role: "staff" | "student";
    roll_no?: string;
}

interface AuthContextType {
    user: AuthUser | null;
    setUser: (u: AuthUser | null, rememberMe?: boolean) => void;
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
            const sessionData = sessionStorage.getItem("edunet_user");
            if (sessionData) return JSON.parse(sessionData);

            const localData = localStorage.getItem("edunet_user");
            if (localData) return JSON.parse(localData);

            return null;
        } catch { return null; }
    });

    const handleSetUser = useCallback((u: AuthUser | null, rememberMe: boolean = false) => {
        setUser(u);
        if (u) {
            const data = JSON.stringify(u);
            if (rememberMe) {
                localStorage.setItem("edunet_user", data);
                sessionStorage.removeItem("edunet_user");
            } else {
                sessionStorage.setItem("edunet_user", data);
                localStorage.removeItem("edunet_user");
            }
        } else {
            sessionStorage.removeItem("edunet_user");
            localStorage.removeItem("edunet_user");
        }
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
