from flask import Flask, request, jsonify
from pymongo import MongoClient
from twilio.twiml.voice_response import VoiceResponse
from twilio.rest import Client
import os
from dotenv import load_dotenv

load_dotenv()

# Initialize Flask app
app = Flask(__name__)

# Connect to MongoDB
client = MongoClient("mongodb://localhost:27017/")  # Replace with your MongoDB URI
db = client["mbti_db"]
users_collection = db["users"]

# Twilio credentials
TWILIO_ACCOUNT_SID = os.getenv("TWILIO_ACCOUNT_SID")  # Use environment variables for security
TWILIO_AUTH_TOKEN = os.getenv("TWILIO_AUTH_TOKEN")
TWILIO_PHONE_NUMBER = os.getenv("TWILIO_PHONE_NUMBER")

# Initialize Twilio client
twilio_client = Client(TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN)

# Define MBTI questions and scoring rules
mbti_questions = [
    # --- Extraversion (4 questions) ---
    {
        "question": "I feel more energized after spending time with a large group of people.",
        "trait": "extraversion",
        "Yes": 1,  # “Yes” => more extraverted
        "No": -1
    },
    {
        "question": "I tend to prefer quiet time alone over attending social events.",
        "trait": "extraversion",
        "Yes": -1,   # “Yes” => more introverted
        "No": 1
    },
    {
        "question": "I usually speak up quickly in group discussions or meetings.",
        "trait": "extraversion",
        "Yes": 1,   # “Yes” => more extraverted
        "No": -1
    },
    {
        "question": "I find it draining to meet and interact with new people frequently.",
        "trait": "extraversion",
        "Yes": -1,  # “Yes” => more introverted
        "No": 1
    },

    # --- Intuition (4 questions) ---
    {
        "question": "I prefer concrete details and proven facts over abstract ideas.",
        "trait": "intuition",
        "Yes": -1,   
        "No": 1
    },
    {
        "question": "I often daydream or think about future possibilities rather than focusing on present realities.",
        "trait": "intuition",
        "Yes": 1,
        "No": -1
    },
    {
        "question": "I trust my instincts even when I lack specific data to support them.",
        "trait": "intuition",
        "Yes": 1,
        "No": -1
    },
    {
        "question": "I prefer relying on proven methods and established procedures rather than experimenting with novel ideas.",
        "trait": "intuition",
        "Yes": -1,
        "No": 1
    },

    # --- Feeling (4 questions) ---
    {
        "question": "I prioritize logical reasoning over personal feelings when making decisions.",
        "trait": "feeling",
        "Yes": -1,  # “Yes” => leaning toward feeling
        "No": 1
    },
    {
        "question": "I tend to consider how my actions might affect others’ emotions before I decide.",
        "trait": "feeling",
        "Yes": 1,
        "No": -1
    },
    {
        "question": "I find it easy to remain objective and detached when solving problems.",
        "trait": "feeling",
        "Yes": -1,
        "No": 1
    },
    {
        "question": "I often base my decisions on personal values or empathy for others.",
        "trait": "feeling",
        "Yes": 1,
        "No": -1
    },

    # --- Perceiving (4 questions) ---
    {
        "question": "I like having a detailed plan or schedule before starting a project.",
        "trait": "perceiving",
        "Yes": -1,  # “Yes” => leaning toward perceiving
        "No": 1
    },
    {
        "question": "I prefer to keep my options open rather than commit to a final decision too soon.",
        "trait": "perceiving",
        "Yes": 1,
        "No": -1
    },
    {
        "question": "I feel uneasy when tasks or events are left unplanned or ambiguous.",
        "trait": "perceiving",
        "Yes": -1,
        "No": 1
    },
    {
        "question": "I find it exciting to adapt spontaneously to changes in plans.",
        "trait": "perceiving",
        "Yes": 1,
        "No": -1
    }
]

# Endpoint to process MBTI responses
@app.route('/process-mbti', methods=['POST'])
def process_mbti():
    try:
        # Get user responses from the request body
        data = request.json
        user_id = data.get("user_id")
        responses = data.get("responses")

        phone_number = data.get("phone_number")  # e.g. "+1234567890"

        if not user_id or not responses:
            return jsonify({"error": "Missing user_id or responses"}), 400

        # Initialize scores
        scores = {"extraversion": 0, "intuition": 0, "feeling": 0, "perceiving": 0}

        # Calculate scores based on responses
        for response in responses:
            question = response.get("question")
            answer = response.get("answer")

            # Find the question in the predefined list
            for q in mbti_questions:
                if q["question"] == question:
                    trait = q["trait"]
                    scores[trait] += q[answer]
                    break

        # Infer behavioral traits and calculate confidence levels
        behavioral_traits = {
            "social_interaction": {
                "trait": "Enjoys social interaction" if scores["extraversion"] > 0 else "Prefers solitude",
                "confidence": abs(scores["extraversion"]) / len(responses)  # Normalize confidence
            },
            "thinking_style": {
                "trait": "Focuses on details" if scores["intuition"] > 0 else "Thinks big picture",
                "confidence": abs(scores["intuition"]) / len(responses)
            },
            "decision_making": {
                "trait": "Logical decision-maker" if scores["feeling"] > 0 else "Empathetic decision-maker",
                "confidence": abs(scores["feeling"]) / len(responses)
            },
            "planning_style": {
                "trait": "Plans ahead" if scores["perceiving"] > 0 else "Flexible planner",
                "confidence": abs(scores["perceiving"]) / len(responses)
            }
        }

        # Infer MBTI type
        mbti_type = (
            "E" if scores["extraversion"] > 0 else "I",
            "S" if scores["intuition"] > 0 else "N",
            "T" if scores["feeling"] > 0 else "F",
            "J" if scores["perceiving"] > 0 else "P"
        )
        mbti_type = "".join(mbti_type)

        # Store results in MongoDB (with behavioral traits and confidence)
        user_data = {
            "user_id": user_id,
            "phone_number": phone_number,
            "mbti_traits": {
                "type": mbti_type,
                "behavioral_traits": behavioral_traits
            }
        }
        users_collection.insert_one(user_data)

        # Return the MBTI type and behavioral traits
        return jsonify({
            "message": "MBTI processed successfully",
            "mbti_type": mbti_type,
            "behavioral_traits": behavioral_traits
        }), 200

    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/trigger-intervention', methods=['POST'])
def trigger_intervention():
    """
    Endpoint to be called when the user opens an avoided app.
    This will place an outbound Twilio call to the user's phone number
    and deliver a short message, encouraging them to stop using the app.
    """
    try:
        data = request.json
        user_id = data.get("user_id")
        avoided_app = data.get("app_name", "this app")  # e.g., "Instagram"

        if not user_id:
            return jsonify({"error": "Missing user_id"}), 400

        # Fetch user's record from DB
        user_record = users_collection.find_one({"user_id": user_id})
        if not user_record:
            return jsonify({"error": "User not found"}), 404

        # Make sure we have a phone number
        phone_number = user_record.get("phone_number")
        if not phone_number:
            return jsonify({"error": "User does not have a phone number on file"}), 400

        # The MBTI type if we want to customize the message (e.g. "INTP")
        user_mbti_type = user_record["mbti_traits"]["type"]

        # Initiate the call via Twilio
        # Twilio will fetch instructions from /call-response (defined below)
        # Make sure that /call-response is publicly accessible (e.g. using ngrok in development)
        call = twilio_client.calls.create(
            to=phone_number,
            from_=TWILIO_PHONE_NUMBER,
            url = f"https://298f-2620-cc-8000-1c83-d1e1-76df-dd1e-9aec.ngrok-free.app/call-response?user_id={user_id}&app_name={avoided_app}",
            method="GET"  # Use GET to fetch TwiML instructions from /call-response
        
        )

        return jsonify({
            "message": "Call initiated",
            "call_sid": call.sid
        }), 200

    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route('/call-response', methods=['GET', 'POST'])
def call_response():
    """
    Twilio requests this endpoint for TwiML instructions on what to say.
    We can tailor the message using the user's MBTI type or any other data.
    """
    user_id = request.args.get('user_id')
    avoided_app = request.args.get('app_name', 'that app')  # optional fallback

    # Retrieve user to grab MBTI or other preferences
    user_record = users_collection.find_one({"user_id": user_id})
    mbti_type = user_record["mbti_traits"]["type"] if user_record else "Unknown"

    # Craft message
    # You might use if/else or an LLM to tailor the message to the MBTI type.
    message = (f"Hey there! It looks like you're about to use {avoided_app} again. "
               f"As a {mbti_type}, remember your goals! Let's take a step back and focus. You got this!")

    # Build TwiML response
    resp = VoiceResponse()
    resp.say(message, voice='alice')
    # Optionally hang up or play another prompt
    resp.hangup()

    return str(resp)



# Run the app
if __name__ == '__main__':
    app.run(debug=True, port=5000)