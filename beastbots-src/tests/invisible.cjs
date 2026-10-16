const mc=require('minecraft-protocol');const data=require('minecraft-data')('1.21.11');
const client=mc.createClient({host:'127.0.0.1',port:25577,username:'BeastTestClient',auth:'offline',version:'1.21.11',hideErrors:true});
const ids=new Set(),invisible=new Set(),skins=new Map();
client.on('spawn_entity',p=>{if(p.type===data.entitiesByName.player.id)ids.add(p.entityId);});
client.on('entity_metadata',p=>{for(const v of p.metadata??[])if(v.key===0&&(v.value&0x20))invisible.add(p.entityId);});
client.on('player_info',p=>{for(const d of p.data??[])if(d.player)skins.set(d.player.name,d.player.properties?.some(x=>x.name==='textures'));});
client.on('position',p=>client.write('teleport_confirm',{teleportId:p.teleportId}));client.on('error',e=>{console.log(e.message);process.exitCode=1;});
setTimeout(()=>{const bots=[...skins].filter(([name])=>name!=='BeastTestClient');console.log(JSON.stringify({tabBots:bots.length,allSkins:bots.every(([,skin])=>skin),nearbyPlayers:ids.size,invisiblePlayers:[...ids].filter(id=>invisible.has(id)).length}));if(bots.length!==10||!bots.every(([,skin])=>skin)||ids.size<10||[...ids].some(id=>!invisible.has(id)))process.exitCode=1;client.end();setTimeout(()=>process.exit(process.exitCode??0),500);},12000);
