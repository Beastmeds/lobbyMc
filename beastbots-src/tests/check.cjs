const mc=require('minecraft-protocol');
const client=mc.createClient({host:'127.0.0.1',port:25577,username:'BeastTestClient',auth:'offline',version:'1.21.11',hideErrors:true});
const players=new Map();let joined=false;
client.on('login',()=>{joined=true;console.log('Test client logged in');});
client.on('player_info',p=>{for(const d of p.data??[]){const name=d.player?.name??d.name;if(name)players.set(name,{uuid:d.uuid,properties:(d.player?.properties??d.properties??[]).map(p=>p.name),listed:d.listed});}});
client.on('position',p=>{client.write('teleport_confirm',{teleportId:p.teleportId});});
client.on('error',e=>{console.log('ERROR',e.message);process.exitCode=1;});
client.on('kick_disconnect',p=>console.log('Kicked',p));
setTimeout(()=>{console.log('Observed players',JSON.stringify([...players]));if(!joined||players.size<11||[...players].filter(([name])=>name!=='BeastTestClient').some(([,p])=>!p.properties.includes('textures')))process.exitCode=1;client.end('Test complete');setTimeout(()=>process.exit(process.exitCode??0),500);},9000);
