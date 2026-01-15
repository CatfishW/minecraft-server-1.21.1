from openai import OpenAI
import json

OPENROUTER_API_KEY = "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"
OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"

client = OpenAI(
    base_url=OPENROUTER_BASE_URL,
    api_key=OPENROUTER_API_KEY,
)

prompt = """
Generate a JSON list of 60 characters from Life is Strange, Breaking Bad, and Better Call Saul.
Each character should be an object: {"name": "...", "theme": "...", "desc": "..."}.
Distribute them roughly equally among the three series.
Descriptions should be brief but thematic (1-2 sentences).
Make sure to include all main characters and some interesting side characters.
Return ONLY a valid JSON array.
"""

completion = client.chat.completions.create(
    model="xiaomi/mimo-v2-flash:free",
    messages=[{"role": "user", "content": prompt}],
)
print(completion.choices[0].message.content)
