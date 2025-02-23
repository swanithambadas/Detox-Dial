# python-backend/app.py
import json
import openai
from flask import Flask, request, jsonify
from flask_cors import CORS
import logging
import os

# Set up logging
logging.basicConfig(level=logging.DEBUG)
logger = logging.getLogger(__name__)

# Initialize Flask app
app = Flask(__name__)
CORS(app)

# Use environment variable
#openai.api_key = os.getenv('OPENAI_API_KEY')

# Basic routes for testing
@app.route('/ping', methods=['GET'])
def ping():
    logger.debug("Ping endpoint hit")
    try:
        return jsonify({
            "status": "ok",
            "message": "pong"
        })
    except Exception as e:
        logger.error(f"Error in ping: {str(e)}")
        return jsonify({"error": str(e)}), 500

@app.route('/', methods=['GET'])
def home():
    try:
        return jsonify({
            "status": "ok",
            "message": "DetoxDial Backend is Running"
        })
    except Exception as e:
        print(f"Error in home: {str(e)}")
        return jsonify({"error": str(e)}), 500

# MBTI routes
@app.route('/mbti/questions', methods=['GET'])
def get_mbti_questions():
    logger.debug("MBTI questions endpoint hit")
    try:
        questions = [
            "Do you prefer working in teams or alone?",
            "Do you focus more on details or big picture?",
            "Are you more energized by social interactions or solitude?",
            "Do you make decisions based on logic or feelings?",
            "Do you prefer planned activities or spontaneous ones?",
            "Are you more practical or theoretical in your thinking?",
            "Do you express emotions easily or keep them private?",
            "Is your workspace organized or flexible?",
            "Do you prefer routine or variety in your daily life?",
            "Are you more focused on present reality or future possibilities?",
            "Do you make decisions quickly or prefer to take time?",
            "Do you prefer direct or tactful communication?",
            "Are you more comfortable with facts or ideas?",
            "Do you seek harmony or truth in discussions?",
            "Are you deadline-oriented or relaxed about time?",
            "Do you prefer concrete or abstract discussions?"
        ]
        return jsonify({
            "status": "ok",
            "questions": questions
        })
    except Exception as e:
        logger.error(f"Error in get_mbti_questions: {str(e)}")
        return jsonify({"error": str(e)}), 500

@app.route('/generate_questions', methods=['POST'])
def generate_questions():
    """
    Generates exactly 16 MBTI questions in yes/no format.
    The response is returned as a JSON array of questions.
    """
    prompt = "Generate exactly 16 MBTI questions in yes/no format. Provide them as a JSON array of strings."
    try:
        response = openai.ChatCompletion.create(
            model="gpt-3.5-turbo",
            messages=[{"role": "system", "content": prompt}],
            max_tokens=300,
            temperature=0.7,
        )
        output = response.choices[0].message['content'].strip()
        # Try parsing the response as JSON; if it fails, fall back to splitting by newlines.
        try:
            questions = json.loads(output)
            if not isinstance(questions, list):
                questions = [q.strip() for q in output.split('\n') if q.strip()]
        except Exception:
            questions = [q.strip() for q in output.split('\n') if q.strip()]
        return jsonify({"questions": questions})
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/analyze_answers', methods=['POST'])
def analyze_answers():
    """
    Analyzes the list of Yes/No answers from the MBTI test and returns the final 4-letter personality type.
    Expects a JSON payload with a key "user_answers" (a list of responses).
    """
    data = request.get_json()
    user_answers = data.get("user_answers", [])
    
    prompt = (
        "You are an MBTI test analyzer. Given the following answers to 16 yes/no questions, "
        "determine the final MBTI personality type. The answers are provided as a list of 'Yes' or 'No' responses. "
        "Respond with only the 4-letter MBTI type (for example, 'INTJ').\n"
        "Answers: " + str(user_answers)
    )
    try:
        response = openai.ChatCompletion.create(
            model="gpt-3.5-turbo",
            messages=[{"role": "system", "content": prompt}],
            max_tokens=50,
            temperature=0.7,
        )
        output = response.choices[0].message['content'].strip()
        # Extract the first 4 letters to get the MBTI type (assuming response is well formatted)
        mbti = output[:4].upper()
        return jsonify({"final_personality": mbti})
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/chat', methods=['POST'])
def chat():
    """
    Receives the user's message along with their MBTI personality.
    Returns a response that is toned (sarcastic, friendly, etc.) based on the personality.
    """
    try:
        # Log the raw request
        logger.debug(f"Raw request data: {request.data}")
        
        data = request.get_json()
        logger.debug(f"Parsed JSON data: {data}")
        
        if not data:
            logger.error("No JSON data received")
            return jsonify({"error": "No data received"}), 400

        personality = data.get("personality", "NEUTRAL")
        user_message = data.get("user_message", "")
        
        logger.debug(f"Received chat request - Personality: {personality}, Message: {user_message}")

        if not user_message:
            logger.error("Empty user message")
            return jsonify({"error": "No message provided"}), 400

        tone_instructions = {
            "INTJ": "respond in a sarcastic and blunt manner",
            "ENFP": "be friendly, encouraging, and motivational",
            "ISTJ": "respond formally and factually",
            "NEUTRAL": "respond in a friendly and helpful manner"
        }
        tone_instruction = tone_instructions.get(personality, "respond in a friendly and helpful manner")
        
        prompt = (
            f"You are a digital wellness chatbot for Detox Dial. "
            f"User personality: {personality}. "
            f"Tone: {tone_instruction}. "
            f"Engage in a brief conversation to help the user resist social media distractions. "
            f"User: {user_message}"
        )
        
        logger.debug(f"Sending prompt to OpenAI: {prompt}")

        try:
            response = openai.ChatCompletion.create(
                model="gpt-3.5-turbo",
                messages=[
                    {"role": "system", "content": "You are a digital wellness assistant helping users with social media addiction."},
                    {"role": "user", "content": prompt}
                ],
                max_tokens=150,
                temperature=0.7,
            )
            chat_response = response.choices[0].message['content'].strip()
            logger.debug(f"Received response from OpenAI: {chat_response}")
            return jsonify({"response": chat_response})
            
        except Exception as e:
            logger.error(f"OpenAI API error: {str(e)}", exc_info=True)
            return jsonify({"error": f"OpenAI API error: {str(e)}"}), 500

    except Exception as e:
        logger.error(f"Server error in /chat: {str(e)}", exc_info=True)
        return jsonify({"error": f"Server error: {str(e)}"}), 500

@app.route('/test_openai', methods=['GET'])
def test_openai():
    try:
        # Test the API key with a simple completion
        response = openai.ChatCompletion.create(
            model="gpt-3.5-turbo",
            messages=[{"role": "system", "content": "Say 'API is working'"}],
            max_tokens=10
        )
        return jsonify({
            "status": "ok",
            "message": "OpenAI API is configured correctly",
            "test_response": response.choices[0].message['content']
        })
    except Exception as e:
        logger.error(f"OpenAI API test failed: {str(e)}")
        return jsonify({
            "status": "error",
            "message": f"OpenAI API error: {str(e)}"
        }), 500

if __name__ == '__main__':
    try:
        logger.info("Starting Flask server...")
        app.run(host='0.0.0.0', port=5000, debug=True)
    except Exception as e:
        logger.error(f"Error starting server: {str(e)}")
