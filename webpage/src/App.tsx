import React, { useState, useRef, useEffect } from 'react';
import { Send, Bot, Sparkles, Copy, RotateCcw } from 'lucide-react';
import { sendMessageToLLM, getPeerConversations } from './api/llm';
import { PeersConversation } from './PeersConversation';
import { ParticleBackground } from './components/ParticleBackground';
import { Sidebar } from './components/Sidebar';

type Message = { role: 'user' | 'assistant'; content: string };

function ChatPage({
  conversation,
  setConversation,
  isTyping,
  setIsTyping,
  isConnected,
  setIsConnected
}: {
  conversation: Message[];
  setConversation: (messages: Message[]) => void;
  isTyping: boolean;
  setIsTyping: (typing: boolean) => void;
  isConnected: boolean;
  setIsConnected: (connected: boolean) => void;
}) {
  const [inputValue, setInputValue] = useState('');
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const [shouldAutoScroll, setShouldAutoScroll] = useState(true);

  const scrollToBottom = () => {
    if (shouldAutoScroll && messagesEndRef.current) {
      messagesEndRef.current.scrollIntoView({ behavior: 'smooth', block: 'end' });
    }
  };

  // Check if user is near bottom of scroll
  const handleScroll = () => {
    if (scrollContainerRef.current) {
      const { scrollTop, scrollHeight, clientHeight } = scrollContainerRef.current;
      const isNearBottom = scrollHeight - scrollTop - clientHeight < 100;
      setShouldAutoScroll(isNearBottom);
    }
  };

  useEffect(() => {
    // Only scroll if user is already at bottom or it's a new message
    if (shouldAutoScroll) {
      scrollToBottom();
    }
  }, [conversation, shouldAutoScroll]);

  const handleSendMessage = async () => {
    if (inputValue.trim() === '' || isTyping) return;

    const newMessage = { role: 'user' as const, content: inputValue };
    const newConversation = [...conversation, newMessage];

    setConversation(newConversation);
    setInputValue('');
    setIsTyping(true);

    try {
      const response = await sendMessageToLLM(inputValue);
      setIsTyping(false);
      setIsConnected(true);
      setConversation([
        ...newConversation,
        { role: 'assistant' as const, content: response }
      ]);
    } catch (error) {
      console.error('Error getting LLM response:', error);
      setIsTyping(false);
      setIsConnected(false);
      setConversation([
        ...newConversation,
        { role: 'assistant' as const, content: 'Sorry, I encountered an error while processing your request. Please check your connection and try again.' }
      ]);
    }
  };

  const copyToClipboard = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
    } catch (err) {
      console.error('Failed to copy text: ', err);
    }
  };

  const regenerateResponse = async (messageIndex: number) => {
    if (messageIndex < 1 || isTyping) return;

    const userMessage = conversation[messageIndex - 1];
    if (userMessage.role !== 'user') return;

    setIsTyping(true);
    try {
      const response = await sendMessageToLLM(userMessage.content);
      setIsTyping(false);
      setIsConnected(true);

      const newConversation = [...conversation];
      newConversation[messageIndex] = { role: 'assistant', content: response };
      setConversation(newConversation);
    } catch (error) {
      console.error('Error regenerating response:', error);
      setIsTyping(false);
      setIsConnected(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  return (
    <div className="flex flex-col h-screen overflow-hidden">
      {/* Main Chat Area */}
      <div
        ref={scrollContainerRef}
        onScroll={handleScroll}
        className="flex-1 overflow-y-auto overflow-x-hidden px-4 py-6"
        style={{ scrollBehavior: 'smooth', WebkitOverflowScrolling: 'touch' }}
      >
        <div className="max-w-3xl mx-auto">
          {conversation.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-full min-h-[60vh] text-center">
              <div className="mb-8">
                <div className="w-16 h-16 bg-gradient-to-br from-cyan-500 via-blue-600 to-purple-600 rounded-2xl flex items-center justify-center shadow-2xl mb-4 mx-auto">
                  <Sparkles className="w-8 h-8 text-white" />
                </div>
                <h2 className="text-2xl font-bold gradient-text mb-3">Welcome to NeuroMesh</h2>
                <p className="text-gray-400 max-w-md mx-auto">
                  Your distributed neural intelligence network. Ask me anything or connect with other neural nodes.
                </p>
              </div>

              {/* Quick start suggestions */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3 max-w-2xl w-full">
                {[
                  "What is neural mesh AI?",
                  "How does distributed intelligence work?",
                  "Write a Python function",
                  "Explain machine learning concepts"
                ].map((suggestion, index) => (
                  <button
                    key={index}
                    onClick={() => setInputValue(suggestion)}
                    className="p-4 text-left bg-gray-800/30 hover:bg-gray-700/50 border border-gray-700/50 hover:border-gray-600/50 rounded-xl transition-all duration-200 text-gray-300 hover:text-white"
                  >
                    <div className="text-sm">{suggestion}</div>
                  </button>
                ))}
              </div>
            </div>
          ) : (
            <div className="space-y-6">
              {conversation.map((message, index) => (
                <div key={index} className="group">
                  <div className={`flex gap-4 ${message.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                    {message.role === 'assistant' && (
                      <div className="flex-shrink-0">
                        <div className="w-8 h-8 rounded-full bg-gradient-to-br from-cyan-500 to-blue-600 flex items-center justify-center">
                          <Bot className="w-4 h-4 text-white" />
                        </div>
                      </div>
                    )}

                    <div className={`max-w-[80%] ${message.role === 'user' ? 'order-1' : ''}`}>
                      <div className={`p-4 rounded-2xl ${message.role === 'user'
                        ? 'bg-gradient-to-br from-cyan-600 to-blue-600 text-white ml-auto'
                        : 'bg-gray-800/50 text-white border border-gray-700/50'
                        }`}>
                        <div className="whitespace-pre-wrap leading-relaxed">
                          {message.content}
                        </div>
                      </div>

                      {/* Message actions */}
                      {message.role === 'assistant' && (
                        <div className="flex items-center gap-2 mt-2 opacity-0 group-hover:opacity-100 transition-opacity">
                          <button
                            onClick={() => copyToClipboard(message.content)}
                            className="flex items-center gap-1 px-2 py-1 text-xs text-gray-400 hover:text-gray-300 hover:bg-gray-800/50 rounded transition-all"
                            title="Copy message"
                          >
                            <Copy className="w-3 h-3" />
                            Copy
                          </button>
                          <button
                            onClick={() => regenerateResponse(index)}
                            disabled={isTyping}
                            className="flex items-center gap-1 px-2 py-1 text-xs text-gray-400 hover:text-gray-300 hover:bg-gray-800/50 rounded transition-all disabled:opacity-50"
                            title="Regenerate response"
                          >
                            <RotateCcw className="w-3 h-3" />
                            Regenerate
                          </button>
                        </div>
                      )}
                    </div>

                    {message.role === 'user' && (
                      <div className="flex-shrink-0">
                        <div className="w-8 h-8 rounded-full bg-gradient-to-br from-purple-500 to-pink-600 flex items-center justify-center">
                          <span className="text-sm font-bold text-white">U</span>
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              ))}

              {isTyping && (
                <div className="flex gap-4">
                  <div className="flex-shrink-0">
                    <div className="w-8 h-8 rounded-full bg-gradient-to-br from-cyan-500 to-blue-600 flex items-center justify-center">
                      <Bot className="w-4 h-4 text-white" />
                    </div>
                  </div>
                  <div className="flex-1">
                    <div className="bg-gray-800/50 border border-gray-700/50 rounded-2xl p-4">
                      <div className="flex items-center gap-2">
                        <div className="flex gap-1">
                          <div className="w-2 h-2 bg-cyan-400 rounded-full animate-bounce"></div>
                          <div className="w-2 h-2 bg-cyan-400 rounded-full animate-bounce" style={{ animationDelay: '0.2s' }}></div>
                          <div className="w-2 h-2 bg-cyan-400 rounded-full animate-bounce" style={{ animationDelay: '0.4s' }}></div>
                        </div>
                        <span className="text-gray-400 text-sm">Neural engine is thinking...</span>
                      </div>
                    </div>
                  </div>
                </div>
              )}
              <div ref={messagesEndRef} />
            </div>
          )}
        </div>
      </div>

      {/* Input Area - Fixed at bottom */}
      <div className="border-t border-gray-700/50 bg-gray-900/95 backdrop-blur-xl p-4">
        <div className="max-w-3xl mx-auto">
          <form onSubmit={(e) => { e.preventDefault(); handleSendMessage(); }}>
            <div className="relative">
              <textarea
                value={inputValue}
                onChange={(e) => setInputValue(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder="Message NeuroMesh..."
                className="w-full bg-gray-800/50 border border-gray-700/50 rounded-xl px-4 py-3 pr-12 text-white placeholder-gray-400 focus:outline-none focus:border-cyan-500/50 resize-none min-h-[52px] max-h-32"
                disabled={isTyping}
                rows={1}
                onInput={(e) => {
                  const target = e.target as HTMLTextAreaElement;
                  target.style.height = 'auto';
                  target.style.height = Math.min(target.scrollHeight, 128) + 'px';
                }}
              />
              <button
                type="submit"
                disabled={isTyping || !inputValue.trim() || !isConnected}
                className="absolute right-2 top-1/2 transform -translate-y-1/2 w-8 h-8 flex items-center justify-center bg-gradient-to-br from-cyan-500 to-blue-600 hover:from-cyan-600 hover:to-blue-700 disabled:opacity-50 disabled:cursor-not-allowed rounded-lg transition-all duration-200"
                title={!isConnected ? 'Disconnected - check your connection' : 'Send message'}
              >
                <Send className="w-4 h-4 text-white" />
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}

function App() {
  const [currentPage, setCurrentPage] = useState('chat');
  const [conversation, setConversation] = useState<Message[]>([]);
  const [isTyping, setIsTyping] = useState(false);
  const [isConnected, setIsConnected] = useState(true);
  const [peerCount, setPeerCount] = useState(0);

  // Check peer count periodically
  useEffect(() => {
    const checkPeers = async () => {
      try {
        const peers = await getPeerConversations();
        setPeerCount(Object.keys(peers).length);
        setIsConnected(true);
      } catch (error) {
        console.error('Failed to fetch peers:', error);
        setIsConnected(false);
      }
    };

    checkPeers();
    const interval = setInterval(checkPeers, 10000);
    return () => clearInterval(interval);
  }, []);

  const clearConversation = () => {
    setConversation([]);
  };

  return (
    <div className="h-screen bg-gradient-to-br from-gray-900 via-gray-800 to-gray-900 text-white flex overflow-hidden">
      <ParticleBackground />

      {/* Sidebar */}
      <Sidebar
        currentPage={currentPage}
        onNavigate={setCurrentPage}
        isConnected={isConnected}
        peerCount={peerCount}
        conversation={conversation}
        onClearConversation={clearConversation}
      />

      {/* Main Content */}
      <div className="flex-1 ml-64 relative z-10">
        {currentPage === 'chat' ? (
          <ChatPage
            conversation={conversation}
            setConversation={setConversation}
            isTyping={isTyping}
            setIsTyping={setIsTyping}
            isConnected={isConnected}
            setIsConnected={setIsConnected}
          />
        ) : (
          <PeersConversation />
        )}
      </div>
    </div>
  );
}

export default App;