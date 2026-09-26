const https = require('https');

const data = JSON.stringify({
  "pipelineTasks": [
    {
      "taskType": "translation",
      "config": {
        "language": {
          "sourceLanguage": "ta",
          "targetLanguage": "en"
        }
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
  res.on('data', (d) => {
    process.stdout.write(d);
  });
});

req.on('error', (error) => {
  console.error(error);
});

req.write(data);
req.end();
