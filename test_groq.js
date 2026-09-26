const https = require('https');

const prompt = `You are a highly empathetic AI livelihood assistant conducting a conversational interview with a rural Indian beneficiary. Extract structured profile data from their spoken answer AND generate a natural, empathetic follow-up question to ask them next. They are missing the following information: preference (self-employment vs wage), work interests/skills, district in Tamil Nadu, mobility (local vs district vs state), 

Extract ONLY these fields if mentioned in the answer (use null if not mentioned):
- edu: The user's education level or qualification.
- preference: What they prefer to do (e.g. self employment, wage employment, own business, government job, etc).
- interests: Array of strings representing their work interests or skills.
- district: The district in Tamil Nadu they are from or want to work in (null if not mentioned).
- mobility: How far they are willing to travel for work (e.g. local village, nearby district, anywhere in state).
- next_question_native: A natural, conversational, and empathetic follow-up question (written directly in the user's regional language code 'ta') to ask for one of the missing pieces of information. This question will be spoken directly to the user, so it must be completely in the target regional language, NOT English.

Respond ONLY with valid JSON. No markdown. Example:
{"edu":"class8","preference":null,"interests":["cattle"],"district":null,"mobility":null, "next_question_native": "உங்கள் கல்வி தகுதி என்ன?"}

User's answer: "நான் பத்தாம் வகுப்பு படித்துள்ளேன்"`;

const data = JSON.stringify({
  "model": "qwen/qwen3.8-27b",
  "messages": [
    {
      "role": "user",
      "content": prompt
    }
  ],
  "temperature": 0.3
});

const options = {
  hostname: 'api.groq.com',
  path: '/openai/v1/chat/completions',
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer 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',
    'Content-Length': Buffer.byteLength(data)
  }
};

const req = https.request(options, (res) => {
  console.log(`STATUS: ${res.statusCode}`);
  let body = '';
  res.on('data', (d) => { body += d; });
  res.on('end', () => { console.log(body); });
});

req.on('error', (error) => {
  console.error(error);
});

req.write(data);
req.end();
