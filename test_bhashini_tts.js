const https = require('https');

const data = JSON.stringify({
  "pipelineTasks": [
    {
      "taskType": "tts",
      "config": {
        "language": {
          "sourceLanguage": "ta"
        },
        "gender": "female",
        "samplingRate": 8000
      }
    }
  ],
  "inputData": {
    "input": [
      {
        "source": "வணக்கம்"
      }
    ]
  }
});

const options = {
  hostname: 'dhruva-api.bhashini.gov.in',
  path: '/services/inference/pipeline',
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': 'n44PHT-uUwqHNAvlBbOZLbxQCI_rq2U8PH3yVqTxJdO_NHLbaWzUu6vumuR9A6mY',
    'Content-Length': Buffer.byteLength(data)
  }
};

const req = https.request(options, (res) => {
  console.log(`STATUS: ${res.statusCode}`);
  let body = '';
  res.on('data', (d) => {
    body += d;
  });
  res.on('end', () => {
    console.log(body.substring(0, 100)); // print part of the response
  });
});

req.on('error', (error) => {
  console.error(error);
});

req.write(data);
req.end();
