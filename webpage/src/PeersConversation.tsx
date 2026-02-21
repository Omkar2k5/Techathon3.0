import React, { useEffect, useState } from 'react';
import { getPeerConversations, Conversation } from './api/llm';
import { Bot, Users, Clock, Wifi, WifiOff, User, RefreshCw } from 'lucide-react';
import { NetworkVisualization } from './components/NetworkVisualization';

export function PeersConversation() {
  const [peerConversations, setPeerConversations] = useState<Record<string, Conversation>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedPeer, setSelectedPeer] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const conversationScrollRef = React.useRef<HTMLDivElement>(null);

  const fetchPeerConversations = async (isInitialLoad = false) => {
    try {
      if (isInitialLoad) {
        setLoading(true);
      } else {
        setRefreshing(true);
      }

      const conversations = await getPeerConversations();
      console.log('Received peer conversations:', conversations);

      // Only update if data actually changed to prevent unnecessary re-renders
      const conversationsChanged = JSON.stringify(conversations) !== JSON.stringify(peerConversations);
      if (conversationsChanged) {
        setPeerConversations(conversations);
      }

      setError(null);

      // Only set initial peer selection, never change it during refresh
      if (isInitialLoad) {
        const peerIps = Object.keys(conversations);
        if (peerIps.length > 0 && !selectedPeer) {
          setSelectedPeer(peerIps[0]);
        }
      }
    } catch (err) {
      console.error('Error loading peer conversations:', err);
      setError('Failed to load peer conversations. Please try again.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    fetchPeerConversations(true); // Initial load

    // Refresh every 10 seconds (reduced frequency)
    const interval = setInterval(() => {
      // Only refresh if not currently viewing a conversation and tab is visible
      if (!document.hidden) {
        fetchPeerConversations(false); // Background refresh
      }
    }, 10000);

    return () => clearInterval(interval);
  }, []); // Empty dependency array - only run once on mount

  const peerIps = Object.keys(peerConversations);

  return (
    <div className="h-screen flex flex-col overflow-hidden">
      <div className="flex-1 overflow-y-auto overflow-x-hidden p-6" style={{ scrollBehavior: 'smooth' }}>
        {/* Page header */}
        <div className="mb-8">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-3xl font-bold gradient-text mb-2">Neural Mesh Dashboard</h2>
              <p className="text-gray-400">Monitor and manage your neural mesh connections</p>
            </div>
            <button
              onClick={fetchPeerConversations}
              disabled={refreshing}
              className="flex items-center gap-2 px-4 py-2 glass rounded-lg hover:bg-gray-700/50 transition-all duration-200 disabled:opacity-50"
              title="Refresh peer data"
            >
              <RefreshCw className={`w-4 h-4 ${refreshing ? 'animate-spin' : ''}`} />
              <span className="text-sm">Refresh</span>
            </button>
          </div>
        </div>

        {loading && peerIps.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16">
            <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-cyan-500 mb-4"></div>
            <p className="text-gray-400">Scanning network for neural nodes...</p>
          </div>
        ) : error ? (
          <div className="text-center bg-red-900/20 border border-red-500/30 rounded-xl p-6">
            <div className="text-red-400 text-lg font-semibold mb-2">Connection Error</div>
            <p className="text-red-300 mb-4">{error}</p>
            <button
              onClick={fetchPeerConversations}
              className="px-4 py-2 bg-red-600 hover:bg-red-700 rounded-lg transition-colors"
            >
              Retry Connection
            </button>
          </div>
        ) : peerIps.length === 0 ? (
          <div className="text-center py-16">
            <div className="relative inline-block mb-6">
              <div className="w-20 h-20 bg-gradient-to-br from-gray-700 to-gray-800 rounded-2xl flex items-center justify-center shadow-xl">
                <Users className="w-10 h-10 text-gray-400" />
              </div>
              <div className="absolute -inset-2 bg-gradient-to-r from-gray-600/20 to-gray-700/20 rounded-3xl blur-xl"></div>
            </div>
            <h3 className="text-2xl font-bold text-white mb-3">No Neural Nodes Detected</h3>
            <p className="text-gray-400 max-w-lg mx-auto mb-6 leading-relaxed">
              No peer conversations are currently available. Make sure other NeuroMesh nodes are online and connected to the same network.
            </p>
            <div className="glass p-6 rounded-xl max-w-md mx-auto">
              <h4 className="text-sm font-semibold text-cyan-400 mb-3">Quick Setup Guide:</h4>
              <ul className="text-sm text-gray-400 space-y-2 text-left">
                <li className="flex items-center gap-2">
                  <div className="w-2 h-2 bg-cyan-400 rounded-full"></div>
                  <span>Ensure devices are on the same WiFi network</span>
                </li>
                <li className="flex items-center gap-2">
                  <div className="w-2 h-2 bg-cyan-400 rounded-full"></div>
                  <span>Check firewall settings allow NeuroMesh</span>
                </li>
                <li className="flex items-center gap-2">
                  <div className="w-2 h-2 bg-cyan-400 rounded-full"></div>
                  <span>Verify Ollama is running on peer devices</span>
                </li>
              </ul>
            </div>
          </div>
        ) : (
          <div className="space-y-8">
            {/* Enhanced Network Visualization */}
            <NetworkVisualization
              peerCount={peerIps.length}
              llmHosts={Object.values(peerConversations).filter(p => p.host_info.is_llm_host).length}
              totalMessages={Object.values(peerConversations).reduce((total, conv) => total + conv.messages.length, 0)}
            />

            {/* Enhanced Peer Selection */}
            <div className="mb-8">
              <div className="flex items-center justify-between mb-6">
                <div className="flex items-center gap-3">
                  <Users className="w-6 h-6 text-cyan-400" />
                  <h3 className="text-2xl font-bold gradient-text">Neural Mesh Network</h3>
                </div>
                <div className="text-sm text-gray-400">
                  {peerIps.length} neural node{peerIps.length !== 1 ? 's' : ''} in mesh
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                {peerIps.map((ip, index) => {
                  const peer = peerConversations[ip];
                  return (
                    <button
                      key={ip}
                      onClick={() => setSelectedPeer(ip)}
                      className={`group p-6 rounded-2xl border transition-all duration-300 text-left hover:scale-105 slide-in-up ${selectedPeer === ip
                        ? 'glass border-cyan-500/50 shadow-2xl glow-animation'
                        : 'glass border-gray-700/50 hover:border-gray-600/50'
                        }`}
                      style={{ animationDelay: `${index * 0.1}s` }}
                    >
                      <div className="flex items-center justify-between mb-4">
                        <div className="flex items-center gap-3">
                          <div className={`w-4 h-4 rounded-full animate-pulse ${peer.host_info.is_llm_host ? 'bg-green-400' : 'bg-gray-400'}`}></div>
                          <span className="font-mono text-lg font-bold text-cyan-400">{ip}</span>
                        </div>
                        <div className="flex items-center gap-2">
                          {peer.host_info.is_llm_host ? (
                            <div className="flex items-center gap-1">
                              <Wifi className="w-5 h-5 text-green-400" />
                              <div className="w-2 h-2 bg-green-400 rounded-full animate-pulse"></div>
                            </div>
                          ) : (
                            <WifiOff className="w-5 h-5 text-gray-400" />
                          )}
                        </div>
                      </div>

                      <div className="space-y-3">
                        <div className="flex items-center gap-2">
                          <User className="w-4 h-4 text-gray-400" />
                          <span className="text-gray-300 font-medium truncate">{peer.host_info.hostname}</span>
                        </div>

                        <div className="flex items-center justify-between">
                          <div className="flex items-center gap-2">
                            <Clock className="w-4 h-4 text-purple-400" />
                            <span className="text-sm text-gray-400">{peer.messages.length} messages</span>
                          </div>
                          {peer.host_info.is_llm_host && (
                            <div className="px-2 py-1 bg-green-500/20 rounded-full border border-green-500/30">
                              <span className="text-xs text-green-400 font-medium">Neural Host</span>
                            </div>
                          )}
                        </div>

                        {/* Connection strength indicator */}
                        <div className="w-full bg-gray-700 rounded-full h-2 overflow-hidden">
                          <div className={`h-full rounded-full transition-all duration-1000 ${peer.host_info.is_llm_host
                            ? 'bg-gradient-to-r from-green-400 to-emerald-500 w-full'
                            : 'bg-gradient-to-r from-gray-400 to-gray-500 w-3/4'
                            }`}></div>
                        </div>
                      </div>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Selected Peer's Conversation */}
            {selectedPeer && peerConversations[selectedPeer] && (
              <div className="glass border border-gray-700/50 rounded-2xl p-6 shadow-2xl">
                <div className="mb-6 border-b border-gray-700/50 pb-4">
                  <div className="flex items-center justify-between">
                    <div>
                      <h2 className="text-xl font-semibold text-cyan-400 mb-2">
                        Neural Node: {selectedPeer}
                      </h2>
                      <div className="flex items-center gap-4 text-sm text-gray-400">
                        <div className="flex items-center gap-2">
                          <User className="w-4 h-4" />
                          <span>Host: {peerConversations[selectedPeer].host_info.hostname}</span>
                        </div>
                        <div className="flex items-center gap-2">
                          {peerConversations[selectedPeer].host_info.is_llm_host ? (
                            <>
                              <Wifi className="w-4 h-4 text-green-400" />
                              <span className="text-green-400 font-medium">Neural Engine Online</span>
                            </>
                          ) : (
                            <>
                              <WifiOff className="w-4 h-4 text-gray-400" />
                              <span>No Neural Engine</span>
                            </>
                          )}
                        </div>
                      </div>
                    </div>
                    <div className="text-right">
                      <div className="text-2xl font-bold text-white">
                        {peerConversations[selectedPeer].messages.length}
                      </div>
                      <div className="text-xs text-gray-400">Messages</div>
                    </div>
                  </div>
                </div>

                <div
                  ref={conversationScrollRef}
                  className="space-y-4 max-h-96 overflow-y-auto custom-scrollbar"
                  style={{ scrollBehavior: 'smooth' }}
                >
                  {peerConversations[selectedPeer].messages.length === 0 ? (
                    <div className="text-center py-8">
                      <div className="text-gray-400 mb-2">No messages yet</div>
                      <div className="text-sm text-gray-500">
                        This neural node hasn't exchanged any messages yet.
                      </div>
                    </div>
                  ) : (
                    peerConversations[selectedPeer].messages.map((message, index) => (
                      <div
                        key={`${message.timestamp}-${index}`}
                        className={`flex items-start gap-4 ${message.message_type === 'Question' ? 'flex-row-reverse' : ''
                          }`}
                      >
                        <div className={`flex-shrink-0 ${message.message_type === 'Question' ? 'order-2' : ''
                          }`}>
                          {message.message_type === "Response" ? (
                            <div className="w-8 h-8 rounded-full bg-gradient-to-r from-cyan-500 to-blue-600 flex items-center justify-center">
                              <Bot className="w-4 h-4 text-white" />
                            </div>
                          ) : (
                            <div className="w-8 h-8 rounded-full bg-gradient-to-r from-purple-500 to-pink-600 flex items-center justify-center">
                              <User className="w-4 h-4 text-white" />
                            </div>
                          )}
                        </div>
                        <div className={`flex-1 max-w-2xl ${message.message_type === 'Question' ? 'order-1' : ''
                          }`}>
                          <div className={`p-4 rounded-xl ${message.message_type === 'Question'
                            ? 'bg-gradient-to-r from-purple-600/20 to-pink-600/20 border border-purple-500/30 ml-8'
                            : 'bg-gray-700/50 border border-gray-600/50 mr-8'
                            }`}>
                            <div className="text-xs text-gray-400 mb-2 flex items-center gap-2">
                              <Clock className="w-3 h-3" />
                              {new Date(message.timestamp).toLocaleString()}
                            </div>
                            <p className="text-white whitespace-pre-wrap text-sm leading-relaxed">
                              {message.content}
                            </p>
                          </div>
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}