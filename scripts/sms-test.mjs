import http from 'node:http';
import { timingSafeEqual } from 'node:crypto';
const messages=new Map();
const token=process.env.SMS_TEST_TOKEN;
if(!token) throw new Error('SMS_TEST_TOKEN required');
http.createServer(async(req,res)=>{
  res.setHeader('Cache-Control','no-store');
  const supplied=Buffer.from(req.headers.authorization||''),expected=Buffer.from(`Bearer ${token}`);
  if(supplied.length!==expected.length || !timingSafeEqual(supplied,expected)) {res.writeHead(401).end();return;}
  if(req.method==='POST' && req.url==='/messages') {
    let body='';for await(const chunk of req) {body+=chunk;if(body.length>1024){res.writeHead(413).end();return;}}
    const form=new URLSearchParams(body),phone=form.get('phone'),code=form.get('code');
    if(!/^\+[1-9]\d{7,14}$/.test(phone||'') || !/^\d{6}$/.test(code||'')){res.writeHead(400).end();return;}
    messages.set(phone,{code,expires:Date.now()+300000});res.writeHead(204).end();return;
  }
  if(req.method==='GET' && req.url.startsWith('/messages?')) {
    const m=messages.get(new URL(req.url,'http://localhost').searchParams.get('phone'));
    if(!m || m.expires<Date.now()){res.writeHead(404).end();return;}
    res.setHeader('Content-Type','application/json');res.end(JSON.stringify(m));return;
  }
  res.writeHead(404).end();
}).listen(8026,'0.0.0.0');
setInterval(()=>{for(const[k,v]of messages)if(v.expires<Date.now())messages.delete(k);},60000).unref();
