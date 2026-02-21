import React from 'react';
import { Brain, MessageCircle, Users, Workflow } from 'lucide-react';

interface NavigationProps {
    currentPage: string;
    onNavigate: (page: string) => void;
    isConnected: boolean;
    peerCount?: number;
}

export const Navigation: React.FC<NavigationProps> = ({
    currentPage,
    onNavigate,
    isConnected,
    peerCount = 0
}) => {
    return (
        <header className="fixed top-0 left-0 right-0 border-b border-gray-700/50 glass shadow-2xl backdrop-blur-xl z-50">
            <div className="max-w-7xl mx-auto px-4 py-4">
                <div className="flex items-center justify-between">
                    {/* Logo and brand */}
                    <div className="flex items-center gap-4">
                        <div className="relative neural-node">
                            <div className="w-12 h-12 bg-gradient-to-br from-cyan-500 to-blue-600 rounded-xl flex items-center justify-center shadow-lg neural-glow">
                                <Brain className="h-6 w-6 text-white" />
                            </div>
                            <div className={`absolute -top-1 -right-1 w-4 h-4 rounded-full animate-pulse border-2 border-gray-900 ${isConnected ? 'bg-green-400' : 'bg-red-400'}`}></div>
                        </div>
                        <div>
                            <h1 className="text-xl md:text-2xl font-bold gradient-text">
                                NeuroMesh
                            </h1>
                            <div className="flex items-center gap-2 mt-1">
                                <Workflow className="w-3 h-3 text-cyan-400" />
                                <p className="text-xs text-gray-400 hidden sm:block">
                                    {currentPage === 'chat' ? 'Neural Intelligence Chat' : 'Neural Mesh Dashboard'}
                                </p>
                                <p className="text-xs text-gray-400 sm:hidden">Neural AI</p>
                            </div>
                        </div>
                    </div>

                    {/* Navigation and status */}
                    <div className="flex items-center gap-3">
                        {/* Status indicators */}
                        <div className="hidden lg:flex items-center gap-3">
                            <div className="glass px-3 py-2 rounded-full">
                                <div className="flex items-center gap-2">
                                    <div className={`w-2 h-2 rounded-full animate-pulse ${isConnected ? 'bg-green-400' : 'bg-red-400'}`}></div>
                                    <span className={`text-xs font-medium ${isConnected ? 'text-green-400' : 'text-red-400'}`}>
                                        {isConnected ? 'Online' : 'Offline'}
                                    </span>
                                </div>
                            </div>
                            {peerCount > 0 && (
                                <div className="glass px-3 py-2 rounded-full">
                                    <div className="flex items-center gap-2">
                                        <Users className="w-3 h-3 text-cyan-400" />
                                        <span className="text-xs text-cyan-400 font-medium">{peerCount} Peers</span>
                                    </div>
                                </div>
                            )}
                        </div>

                        {/* Navigation buttons */}
                        <div className="flex items-center gap-2">
                            <button
                                onClick={() => onNavigate('chat')}
                                className={`flex items-center gap-2 px-4 py-2 rounded-lg transition-all duration-200 ${currentPage === 'chat'
                                    ? 'bg-gradient-to-r from-cyan-600 to-blue-600 text-white shadow-lg'
                                    : 'text-gray-400 hover:text-white hover:bg-gray-700/50'
                                    }`}
                            >
                                <MessageCircle className="h-4 w-4" />
                                <span className="font-medium text-sm hidden sm:inline">Chat</span>
                            </button>
                            <button
                                onClick={() => onNavigate('peers')}
                                className={`flex items-center gap-2 px-4 py-2 rounded-lg transition-all duration-200 ${currentPage === 'peers'
                                    ? 'bg-gradient-to-r from-blue-600 to-purple-600 text-white shadow-lg'
                                    : 'text-gray-400 hover:text-white hover:bg-gray-700/50'
                                    }`}
                            >
                                <Users className="h-4 w-4" />
                                <span className="font-medium text-sm hidden sm:inline">Mesh</span>
                                {peerCount > 0 && (
                                    <span className="bg-cyan-500 text-white text-xs px-2 py-0.5 rounded-full font-bold">
                                        {peerCount}
                                    </span>
                                )}
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </header>
    );
};