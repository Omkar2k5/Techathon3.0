import React from 'react';
import { Brain, Cpu, Layers, Shield } from 'lucide-react';

interface NetworkVisualizationProps {
    peerCount: number;
    llmHosts: number;
    totalMessages: number;
}

export const NetworkVisualization: React.FC<NetworkVisualizationProps> = ({
    peerCount,
    llmHosts,
    totalMessages
}) => {
    return (
        <div className="relative overflow-hidden bg-gradient-to-br from-gray-900/50 to-gray-800/50 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-6">
            {/* Background pattern */}
            <div className="absolute inset-0 opacity-10">
                <div className="absolute top-4 left-4 w-32 h-32 bg-gradient-to-br from-cyan-400 to-blue-600 rounded-full blur-3xl"></div>
                <div className="absolute bottom-4 right-4 w-24 h-24 bg-gradient-to-br from-purple-400 to-pink-600 rounded-full blur-2xl"></div>
            </div>

            <div className="relative z-10">
                <div className="flex items-center gap-3 mb-6">
                    <div className="neural-node">
                        <Brain className="w-8 h-8 text-cyan-400" />
                    </div>
                    <div>
                        <h3 className="text-xl font-bold gradient-text">NeuroMesh Status</h3>
                        <p className="text-sm text-gray-400">Real-time neural mesh monitoring</p>
                    </div>
                </div>

                <div className="grid grid-cols-3 gap-6">
                    <div className="glass rounded-xl p-6 text-center group hover:scale-105 transition-transform duration-200">
                        <div className="w-16 h-16 mx-auto mb-4 bg-gradient-to-br from-cyan-500/20 to-blue-600/20 rounded-full flex items-center justify-center group-hover:neural-glow">
                            <Layers className="w-8 h-8 text-cyan-400" />
                        </div>
                        <div className="text-3xl font-bold text-white mb-2">{peerCount}</div>
                        <div className="text-sm text-gray-400">Neural Nodes</div>
                    </div>

                    <div className="glass rounded-xl p-6 text-center group hover:scale-105 transition-transform duration-200">
                        <div className="w-16 h-16 mx-auto mb-4 bg-gradient-to-br from-green-500/20 to-emerald-600/20 rounded-full flex items-center justify-center group-hover:neural-glow">
                            <Cpu className="w-8 h-8 text-green-400" />
                        </div>
                        <div className="text-3xl font-bold text-white mb-2">{llmHosts}</div>
                        <div className="text-sm text-gray-400">Neural Engines</div>
                    </div>

                    <div className="glass rounded-xl p-6 text-center group hover:scale-105 transition-transform duration-200">
                        <div className="w-16 h-16 mx-auto mb-4 bg-gradient-to-br from-purple-500/20 to-pink-600/20 rounded-full flex items-center justify-center group-hover:neural-glow">
                            <Shield className="w-8 h-8 text-purple-400" />
                        </div>
                        <div className="text-3xl font-bold text-white mb-2">{totalMessages}</div>
                        <div className="text-sm text-gray-400">Messages</div>
                    </div>
                </div>
            </div>
        </div>
    );
};