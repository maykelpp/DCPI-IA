const $=id=>document.getElementById(id);let voice=localStorage.getItem('voice')||'female';$('voice').value=voice;$('key').value=localStorage.getItem('geminiKey')||'';
function toggleSettings(){$('settings').classList.toggle('hidden')}
function saveSettings(){localStorage.setItem('geminiKey',$('key').value.trim());voice=$('voice').value;localStorage.setItem('voice',voice);toggleSettings();say('Configuración guardada.')}
function add(text,who){const el=document.createElement('div');el.className='bubble '+who;el.textContent=text;$('chat').appendChild(el);$('chat').scrollTop=$('chat').scrollHeight}
function say(text){if(window.DCPI)DCPI.speak(text,voice)}
function listen(){$('status').textContent='Escuchando...';$('orb').classList.add('listening');DCPI.listen()}
window.onSpeechResult=text=>{$('orb').classList.remove('listening');$('status').textContent='Listo para escucharte';if(text){$('prompt').value=text;send()}}
window.onAIReply=text=>{add(text,'ai');$('status').textContent='Respuesta lista';say(text)}
function send(){const text=$('prompt').value.trim();if(!text)return;$('prompt').value='';add(text,'user');const q=text.toLowerCase();
if(q.includes('busca video')||q.includes('buscar video')||q.includes('youtube')){DCPI.videos(text.replace(/busca(r)? video(s)?|youtube/ig,'').trim()||text);add('Abrí YouTube con tu búsqueda.','ai');say('Aquí tienes los resultados de video.');return}
if(q.startsWith('busca ')||q.startsWith('buscar ')){DCPI.search(text.replace(/^buscar?\s+/i,''));add('Abrí una búsqueda en Internet.','ai');say('Abriendo la búsqueda.');return}
const key=localStorage.getItem('geminiKey');if(!key){add('Para conversar con Gemini, agrega tu clave gratuita en Configuración. También puedo abrir búsquedas web y videos.','ai');say('Necesitas configurar tu clave gratuita de Gemini.');toggleSettings();return}
$('status').textContent='Pensando...';DCPI.askGemini(text,key)}
