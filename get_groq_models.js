const https = require('https');
const options = {
  hostname: 'api.groq.com',
  path: '/openai/v1/models',
  method: 'GET',
  headers: {
    'Authorization': 'Bearer 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'
  }
};
const req = https.request(options, (res) => {
  let body = '';
  res.on('data', (d) => { body += d; });
  res.on('end', () => { 
    const models = JSON.parse(body).data;
    console.log(models.map(m => m.id).join(', '));
  });
});
req.end();
