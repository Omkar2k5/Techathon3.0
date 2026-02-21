import os
from dotenv import load_dotenv
from langchain_community.utilities import SQLDatabase
import streamlit as st
from langchain_core.messages import AIMessage, HumanMessage
from langchain_core.prompts import ChatPromptTemplate
from langchain_google_genai import ChatGoogleGenerativeAI
from langchain_core.runnables import RunnablePassthrough
from langchain_core.output_parsers import StrOutputParser


def init_db(db_user: str, db_password: str, db_host: str, db_port: str, db_name: str) -> SQLDatabase:
    db_uri = f"mysql+mysqlconnector://{db_user}:{db_password}@{db_host}:{db_port}/{db_name}"
    return SQLDatabase.from_uri(db_uri)


def get_sqlchain(db):
    template = """
    You are a data analyst at a company. You are interacting with a user who is asking you questions about the company's database.
    Based on the table schema below, write a SQL query that would answer the user's question. Take the conversation history into account.

    <SCHEMA>{schema}</SCHEMA>

    Conversation History: {chat_history}

    Write only the SQL query and nothing else. Do not wrap the SQL query in any other text, not even backticks.

    Your turn:
    Question: {question}
    SQL Query:
    """

    prompt = ChatPromptTemplate.from_template(template)
    llm = ChatGoogleGenerativeAI(model="gemini-2.0-flash-exp")

    def get_schema(_):
        return db.get_table_info()

    return (
            RunnablePassthrough.assign(schema=get_schema)
            | prompt
            | llm
            | StrOutputParser()
    )


def get_response(user_query: str, db: SQLDatabase, chat_history: list):
    sql_chain = get_sqlchain(db)
    query = sql_chain.invoke({"question": user_query, "chat_history": chat_history})

    return f"Here is the generated SQL query:\n```sql\n{query}\n```"


if "chat_history" not in st.session_state:
    st.session_state.chat_history = [
        AIMessage("Hello! I am a SQL Assistant. Ask me anything about your database."),
        HumanMessage(content="How many albums are there in the database?"),
    ]

load_dotenv()
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")

st.set_page_config(
    page_title="Database Chat Assistant",
    page_icon="🤖",
    layout="wide",
    initial_sidebar_state="expanded"
)

st.title("🔍 Database Chat Assistant")
st.markdown("---")

with st.sidebar:
    st.markdown("### ⚙️ Database Connection")
    with st.form("db_connection"):
        st.text_input("Host", value="localhost", key="Host")
        col1, col2 = st.columns(2)
        with col1:
            st.text_input("Port", value="3306", key="Port")
        with col2:
            st.text_input("User", value="root", key="User")
        st.text_input("Password", type="password", key="Password")
        st.text_input("Database Name", key="Database")
        connect_button = st.form_submit_button("🔌 Connect to Database")

        if connect_button:
            with st.spinner("🔄 Establishing connection..."):
                try:
                    db = init_db(
                        db_user=st.session_state["User"],
                        db_password=st.session_state["Password"],
                        db_host=st.session_state["Host"],
                        db_port=st.session_state["Port"],
                        db_name=st.session_state["Database"]
                    )
                    st.session_state.db = db
                    st.success("✅ Connected successfully!")
                except Exception as e:
                    st.error(f"❌ Connection failed: {str(e)}")

if hasattr(st.session_state, 'db'):
    st.sidebar.markdown("---")
    st.sidebar.success("🟢 Database Connected")
else:
    st.sidebar.markdown("---")
    st.sidebar.warning("⚠️ Database Not Connected")

chat_container = st.container()
with chat_container:
    for message in st.session_state.chat_history:
        if isinstance(message, AIMessage):
            with st.chat_message("assistant", avatar="🤖"):
                st.markdown(message.content)
        elif isinstance(message, HumanMessage):
            with st.chat_message("user", avatar="👤"):
                st.markdown(message.content)

user_query = st.chat_input("Ask me about your database...", key="user_input")

if user_query is not None and user_query.strip() != "":
    if not hasattr(st.session_state, 'db'):
        st.error("⚠️ Please connect to a database first")
    else:
        st.session_state.chat_history.append(HumanMessage(content=user_query))

        with st.chat_message("user", avatar="👤"):
            st.markdown(user_query)

        with st.chat_message("assistant", avatar="🤖"):
            with st.spinner("🤔 Thinking..."):
                response = get_response(user_query, st.session_state.db, st.session_state.chat_history)
                st.markdown(response)
                st.session_state.chat_history.append(AIMessage(content=response))