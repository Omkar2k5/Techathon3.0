import React from 'react';
import { Brain, MessageCircle, Users, Workflow, Settings, Plus, Trash2 } from 'lucide-react';

interface SidebarProps {
    currentPage: string;
    onNavigate: (page: string) => void;
    isConnected: boolean;
    peerCount: number;
    conversation: any[];
    onClearConversation: () => void;
}

export const Sidebar: React.FC<SidebarProps> = ({
    currentPage,
    onNavigate,
    isConnected,
    peerCount,
    conversation,
    onClearConversation
}) => {
    return (
        <div className="fixed left-0 top-0 h-full w-64 bg-gray-900/95 backdrop-blur-xl border-r border-gray-700/50 z-40 flex flex-col">
            {/* Header */}
            <div className="p-4 border-b border-gray-700/50">
                <div className="flex items-center gap-3">
                    <div className="relative">
                        <div className="w-10 h-10 bg-gradient-to-br from-cyan-500 to-blue-600 rounded-lg flex items-center justify-center shadow-lg">
                            <Brain className="h-5 w-5 text-white" />
                        </div>
                        <div className={`absolute -top-1 -right-1 w-3 h-3 rounded-full animate-pulse border-2 border-gray-900 ${isConnected ? 'bg-green-400' : 'bg-red-400'}`}></div>
                    </div>
                    <div>
                        <h1 className="text-lg font-bold gradient-text">NeuroMesh</h1>
                        <p className="text-xs text-gray-400">Neural Intelligence</p>
                    </div>
                </div>
            </div>

            {/* New Chat Button */}
            <div className="p-4">
                <button
                    onClick={onClearConversation}
                    className="w-full flex items-center gap-3 px-4 py-3 bg-gradient-to-r from-cyan-600/20 to-blue-600/20 hover:from-cyan-600/30 hover:to-blue-600/30 border border-cyan-500/30 rounded-lg transition-all duration-200 text-cyan-400 hover:text-cyan-300"
                >
                    <Plus className="w-4 h-4" />
                    <span className="font-medium">New Chat</span>
                </button>
            </div>

            {/* Navigation */}
            <div className="flex-1 px-4">
                <nav className="space-y-2">
                    <button
                        onClick={() => onNavigate('chat')}
                        className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg transition-all duration-200 ${currentPage === 'chat'
                                ? 'bg-gray-700/50 text-white border border-gray-600/50'
                                : 'text-gray-400 hover:text-white hover:bg-gray-800/50'
                            }`}
                    >
                        <MessageCircle className="w-4 h-4" />
                        <span>Chat</span>
                    </button>

                    <button
                        onClick={() => onNavigate('peers')}
                        className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg transition-all duration-200 ${currentPage === 'peers'
                                ? 'bg-gray-700/50 text-white border border-gray-600/50'
                                : 'text-gray-400 hover:text-white hover:bg-gray-800/50'
                            }`}
                    >
                        <Users className="w-4 h-4" />
                        <span>Neural Mesh</span>
                        {peerCount > 0 && (
                            <span className="ml-auto bg-cyan-500 text-white text-xs px-2 py-0.5 rounded-full font-bold">
                                {peerCount}
                            </span>
                        )}
                    </button>
                </nav>

                {/* Chat History */}
                {conversation.length > 0 && currentPage === 'chat' && (
                    <div className="mt-6">
                        <div className="flex items-center justify-between mb-3">
                            <h3 className="text-sm font-medium text-gray-400">Current Session</h3>
                            <button
                                onClick={onClearConversation}
                                className="text-gray-500 hover:text-red-400 transition-colors"
                                title="Clear conversation"
                            >
                                <Trash2 className="w-3 h-3" />
                            </button>
                        </div>
                        <div className="bg-gray-800/30 rounded-lg p-3 border border-gray-700/30">
                            <div className="text-xs text-gray-400 mb-1">Messages: {conversation.length}</div>
                            <div className="text-xs text-gray-500">
                                {conversation[0]?.content.slice(0, 50)}...
                            </div>
                        </div>
                    </div>
                )}
            </div>

            {/* Status Footer */}
            <div className="p-4 border-t border-gray-700/50">
                <div className="space-y-2">
                    <div className="flex items-center gap-2 text-xs">
                        <div className={`w-2 h-2 rounded-full animate-pulse ${isConnected ? 'bg-green-400' : 'bg-red-400'}`}></div>
                        <span className={`font-medium ${isConnected ? 'text-green-400' : 'text-red-400'}`}>
                            {isConnected ? 'Neural Engine Online' : 'Offline'}
                        </span>
                    </div>
                    <div className="flex items-center gap-2 text-xs text-gray-400">
                        <Workflow className="w-3 h-3" />
                        <span>Distributed Intelligence Network</span>
                    </div>
                </div>
            </div>
        </div>
    );
};