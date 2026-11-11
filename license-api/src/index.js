export default {
 async fetch(req,env) {
  const u=new URL(req.url), cors={"Access-Control-Allow-Origin":"*","Access-Control-Allow-Methods":"GET,POST,PATCH,OPTIONS","Access-Control-Allow-Headers":"Content-Type,Authorization"};
  const out=(v,s=200)=>new Response(JSON.stringify(v),{status:s,headers:{...cors,"Content-Type":"application/json","Cache-Control":"no-store"}});
  if(req.method==="OPTIONS") return new Response(null,{headers:cors});
  const hash=async s=>Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256",new TextEncoder().encode(s)))).map(x=>x.toString(16).padStart(2,"0")).join("");
  const now=()=>Math.floor(Date.now()/1000), body=async()=>{try{return await req.json()}catch{return {}}};
  try {
   if(u.pathname==="/api/health") return out({ok:true});
   if(u.pathname==="/api/trial"&&req.method==="POST"){
    const b=await body(),d=String(b.deviceId||""); if(!d)return out({error:"deviceId_required"},400);
    const h=await hash("locktimer:"+d); let r=await env.DB.prepare("SELECT expires_at FROM trials WHERE device_hash=?").bind(h).first();
    if(!r){await env.DB.prepare("INSERT OR IGNORE INTO trials VALUES(?,?,?)").bind(h,now(),now()+604800).run();r=await env.DB.prepare("SELECT expires_at FROM trials WHERE device_hash=?").bind(h).first();}
    return r.expires_at>now()?out({active:true,trial:true,expiresAt:r.expires_at}):out({active:false,code:"trial_expired"},403);
   }
   if(u.pathname==="/api/activate"&&req.method==="POST"){
    const b=await body(),d=String(b.deviceId||""),k=String(b.key||"").trim().toUpperCase();if(!d||!k)return out({error:"device_and_key_required"},400);
    const kh=await hash(k),dh=await hash("locktimer:"+d),l=await env.DB.prepare("SELECT * FROM licenses WHERE key_hash=?").bind(kh).first();
    if(!l||l.status!=="active"||(l.expires_at&&l.expires_at<=now()))return out({active:false,code:"invalid_or_expired"},403);
    const n=await env.DB.prepare("SELECT COUNT(*) n FROM license_devices WHERE license_id=?").bind(l.id).first(),exists=await env.DB.prepare("SELECT id FROM license_devices WHERE license_id=? AND device_hash=?").bind(l.id,dh).first();
    if(!exists&&n.n>=l.max_devices)return out({active:false,code:"device_limit"},403);
    await env.DB.prepare("INSERT OR IGNORE INTO license_devices(license_id,device_hash,activated_at) VALUES(?,?,?)").bind(l.id,dh,now()).run();
    return out({active:true,trial:false,expiresAt:l.expires_at});
   }
   if(u.pathname==="/api/validate"&&req.method==="POST"){
    const b=await body(),dh=await hash("locktimer:"+String(b.deviceId||""));
    const t=await env.DB.prepare("SELECT expires_at FROM trials WHERE device_hash=?").bind(dh).first();
    if(t&&t.expires_at>now())return out({active:true,trial:true,expiresAt:t.expires_at});
    const l=await env.DB.prepare("SELECT l.expires_at FROM licenses l JOIN license_devices d ON d.license_id=l.id WHERE d.device_hash=? AND l.status='active' LIMIT 1").bind(dh).first();
    if(l&&(!l.expires_at||l.expires_at>now()))return out({active:true,trial:false,expiresAt:l.expires_at});
    return out({active:false,code:t?"trial_expired":"activation_required"},403);
   }
   if(u.pathname.startsWith("/api/admin/")){
    if(!env.ADMIN_TOKEN||req.headers.get("Authorization")!=="Bearer "+env.ADMIN_TOKEN)return out({error:"unauthorized"},401);
    if(u.pathname==="/api/admin/keys"&&req.method==="POST"){
     const b=await body(),count=Math.min(200,Math.max(1,Number(b.count)||1)),days=Math.max(0,Number(b.days)||0),max=Math.min(10,Math.max(1,Number(b.maxDevices)||1)),keys=[];
     for(let i=0;i<count;i++){const raw="LT-"+Array.from(crypto.getRandomValues(new Uint8Array(16))).map(x=>x.toString(16).padStart(2,"0")).join("").toUpperCase().match(/.{1,4}/g).join("-");const exp=days?now()+days*86400:null;await env.DB.prepare("INSERT INTO licenses(key_hash,expires_at,status,max_devices,created_at) VALUES(?,?,'active',?,?)").bind(await hash(raw),exp,max,now()).run();keys.push({key:raw,expiresAt:exp,maxDevices:max});}
     return out({keys},201);
    }
    if(u.pathname==="/api/admin/keys"&&req.method==="GET"){const r=await env.DB.prepare("SELECT id,substr(key_hash,1,12) fingerprint,expires_at,status,max_devices,created_at FROM licenses ORDER BY id DESC LIMIT 200").all();return out({licenses:r.results});}
    const m=u.pathname.match(/^\/api\/admin\/keys\/(\d+)$/);if(m&&req.method==="PATCH"){const b=await body();await env.DB.prepare("UPDATE licenses SET status=? WHERE id=?").bind(b.status==="revoked"?"revoked":"active",Number(m[1])).run();return out({ok:true});}
   }
   return out({error:"not_found"},404);
  }catch(e){return out({error:"server_error",message:String(e)},500);}
 }
};