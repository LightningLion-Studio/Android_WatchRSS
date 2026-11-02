// Execute the checked-out PodJS production codecs under Bun. Never contacts a service.
import {createHash, createHmac, hkdfSync, createCipheriv, createDecipheriv} from 'node:crypto';
import {gzipSync, gunzipSync} from 'node:zlib';
import {readFileSync, writeFileSync} from 'node:fs';
import {WatchCloudCodec, contentChunks} from '/home/wilf/data/OPPO_Develop/PodJS_WatchRSS/src/cloud/codec';
import {bookPackageLines, validateBookPackage, splitBookLines} from '/home/wilf/data/OPPO_Develop/PodJS_WatchRSS/src/books/package';
import {chapterHash, bookFingerprint, bookId, bookArticleId, mergeBookReading} from '/home/wilf/data/OPPO_Develop/PodJS_WatchRSS/src/books/protocol';
import {writeWrss} from '/home/wilf/data/OPPO_Develop/PodJS_WatchRSS/src/cloud/wrss';
import {encodePrivateOverlay, validateMicroArchive, articleOverlayName, decodePrivateOverlay} from '/home/wilf/data/OPPO_Develop/PodJS_WatchRSS/src/cloud/private-overlay';
import {CloudRecordCodec} from '/home/wilf/data/OPPO_Develop/PodJS_WatchRSS/src/cloud/records';
import {cloudRaw} from '/home/wilf/data/OPPO_Develop/PodJS_WatchRSS/src/cloud/compatibility';
let tick=0;
const bytes=(n:number)=>Uint8Array.from({length:n},()=>++tick%251);
const sha=async(b:Uint8Array)=>createHash('sha256').update(b).digest('hex');
const c:any={random:async(n:number)=>bytes(n),sha256:sha,hmac:async(k:Uint8Array,b:Uint8Array)=>createHmac('sha256',k).update(b).digest('hex'),
 hkdf:async(k:Uint8Array,s:Uint8Array,i:Uint8Array)=>new Uint8Array(hkdfSync('sha256',k,s,i,32)),
 encrypt:async(k:Uint8Array,a:Uint8Array,p:Uint8Array)=>{const nonce=bytes(12),v=createCipheriv('aes-256-gcm',k,nonce);v.setAAD(a);return {nonce,ciphertext:new Uint8Array(Buffer.concat([v.update(p),v.final(),v.getAuthTag()]))};},
 decrypt:async(k:Uint8Array,a:Uint8Array,n:Uint8Array,b:Uint8Array)=>{const v=createDecipheriv('aes-256-gcm',k,n);v.setAAD(a);v.setAuthTag(b.slice(-16));return new Uint8Array(Buffer.concat([v.update(b.slice(0,-16)),v.final()]));},
 gzip:async(b:Uint8Array)=>new Uint8Array(gzipSync(b)),gunzip:async(b:Uint8Array,max:number)=>new Uint8Array(gunzipSync(b,{maxOutputLength:Math.max(1,max)}))};
const key=new Uint8Array(32).fill(41), codec=new WatchCloudCodec(c,{require(){}},{writeV1:()=>true,migrationV1:()=>true});
const utf=(s:string)=>new TextEncoder().encode(s), b64=(b:Uint8Array)=>Buffer.from(b).toString('base64');
const unb64=(s:string)=>new Uint8Array(Buffer.from(s,'base64'));
const [mode,path]=process.argv.slice(2);
if(mode==='generate'){
 const source='https://watchrss.local/import-epub/layout3', header={format:'watchrss-book',version:1,title:'测试书😀',chapterCount:2,sourceUrl:source,kind:'epub'};
 const chapters=[{chapterIndex:1,title:'第一章\r\n序',contentText:'甲\r\n乙😀',contentHtml:'<p>甲乙</p>',url:source+'/chapter/1'},{chapterIndex:2,title:'第二章',contentText:'丙\n丁',contentHtml:null,url:source+'/chapter/2'}];
 let packageUtf8='';for await(const line of bookPackageLines(header as any,chapters))packageUtf8+=line;
 const hashes=await Promise.all(chapters.map(v=>chapterHash(v.chapterIndex,v.title,v.contentText,sha))), fingerprint=await bookFingerprint(hashes,sha),id=await bookId(fingerprint,sha);
 const privateBook={bookId:id,fingerprint,title:header.title,chapterCount:2,sourceUrl:source,kind:'epub',packagePath:'books/'+id+'.jsonl'};
 const articleId='layout3-article',stem='bodies/'+await sha(utf(articleId));
 const article={articleId,sourceDeviceId:'phone-fixture',url:'https://example.invalid/private',title:'离线文章',siteName:'fixture',excerpt:'',imageUrl:null,contentHash:'body-hash',importedAt:100,updatedAt:100,rssSourceUrl:null,rssSourceTitle:null,independentSaved:true,independentChangedAt:100,independentSortOrder:1,favoriteSaved:false,favoriteChangedAt:0,favoriteSortOrder:0,watchLaterSaved:false,watchLaterChangedAt:0,watchLaterSortOrder:0,deleted:false,deletedAt:0,readingProgress:0,readingPositionBytes:0,readingPositionContentHash:'',readingPositionChangedAt:0,isRead:false,contentTextEntry:stem+'.txt',contentHtmlEntry:stem+'.html'};
 const zip=new Map<string,Uint8Array>();zip.set('manifest.json',utf(JSON.stringify({format:'watchrss-library-backup',version:3,cloudContentOnly:true,exportedAt:100,sourceCount:0,articleCount:1,savedItemCount:0,readerPresetCount:0,readerFontCount:0,readerBackgroundCount:0,appMetaCount:0})));
 zip.set('articles.json',utf('['+JSON.stringify(article).replace(/}$/,',"futureExact":9007199254740993123456789}')+']'));
 for(const name of ['sources.json','saved_items.json','app_meta.json'])zip.set(name,utf('[]'));
 zip.set(stem+'.txt',utf('离线正文😀'));zip.set(stem+'.html',utf('<p>离线正文😀</p>'));
 const micro=writeWrss(zip);await validateMicroArchive(micro,articleId,c);const microName=await articleOverlayName(articleId,sha);
 const overlay=encodePrivateOverlay([{kind:'article',id:articleId,object:microName,changedAt:100,sourceDeviceId:'phone-fixture'},{kind:'book',id,object:privateBook.packagePath,changedAt:100,sourceDeviceId:'phone-fixture'}]);
 const settings={snapshotContentLayout:3,libraryEpoch:'11111111-1111-4111-8111-111111111111',contentGeneration:'22222222-2222-4222-8222-222222222222',ownerUserId:'offline-fixture'};
 const snapshot=await codec.create({accountKey:key,keyVersion:1,sourceDeviceId:'phone-fixture',sequence:1,snapshotId:'podjs-layout3',createdAtMillis:100,parentHeads:{},observedHeads:{},settings,features:{incompat:['r48.private-overlay.v1'],roCompat:[]},objects:[{name:privateBook.packagePath,bytes:utf(packageUtf8),privateBook,contentSha256:await sha(utf(packageUtf8))},{name:microName,bytes:micro,compress:false,contentSha256:await sha(micro)},{name:'private/overlay-v1.json',bytes:overlay},{name:'future.bin',bytes:utf('unknown object retained')}]});
 // Exercise production raw preservation by authenticating a future descriptor and root token,
 // then asking the real PodJS writer to carry the authenticated document to its next snapshot.
 const alternateRoot=new Uint8Array(32).fill(93);
 const alternate=await codec.create({accountKey:alternateRoot,keyVersion:1,sourceDeviceId:'migration-fixture',sequence:1,snapshotId:'alternate-root',createdAtMillis:100,parentHeads:{},observedHeads:{},objects:[{name:'wrapped.wrss',bytes:micro,compress:false}]});
 const wrapped={...alternate.manifest.objects[0],objectKeyEnvelope:await codec.wrapObjectKey(key,alternateRoot,'33333333-3333-4333-8333-333333333333'),features:{incompat:['r48.object-key.v1'],roCompat:[]}};
 snapshot.manifest.objects.push(wrapped);
 for(const [hash,blob]of alternate.newChunks)snapshot.newChunks.set(hash,blob);
 const entryRef={name:'reader-ref.txt',encoding:'identity',originalBytes:utf('离线正文😀').length,encodedBytes:0,entryRef:{object:microName,entryPath:stem+'.txt',encoding:'identity'},features:{incompat:['r48.entry-ref.v1'],roCompat:[]}};
 snapshot.manifest.objects.push(entryRef as any);
 const original=JSON.stringify(snapshot.manifest, (k,v)=>k==='allChunks'?undefined:v).replace('"name":"future.bin"','"futureNumber":9007199254740993123456789,"name":"future.bin"').replace(/}$/,',"futureRoot":9007199254740993123456789}');
 const salt=bytes(16),derived=await c.hkdf(key,salt,utf('watchrss/cloud/manifest/v2/podjs-layout3')),sealed=await c.encrypt(derived,utf('watchrss-cloud-manifest-v2:podjs-layout3'),utf(original));
 const authenticated=await codec.decryptManifest(key,'podjs-layout3',new Uint8Array(Buffer.concat([Buffer.from('WRSSCM3'),salt,sealed.nonce,sealed.ciphertext])));
 const carried=await codec.create({accountKey:key,keyVersion:1,sourceDeviceId:'phone-fixture',sequence:2,snapshotId:'podjs-layout3-carried',createdAtMillis:101,parentHeads:{},observedHeads:{},settings,previous:authenticated,objects:[]});
 const chunks=Object.fromEntries([...snapshot.newChunks].map(([h,b])=>[h,b64(b)]));
 const bookState={bookId:id,fingerprint,title:header.title,chapterCount:2,deletedChapters:[],deletionClock:0,deleted:false,deletedAt:0,updatedAt:100,sourceDeviceId:'phone-fixture',sourceUrl:source,kind:'epub'};
 const reading={bookId:id,chapterIndex:1,isRead:false,readingProgress:.25,readingPositionBytes:4,readingPositionContentHash:hashes[0],readingPositionChangedAt:200,sourceDeviceId:'reader',favoriteSaved:true,favoriteChangedAt:300,favoriteSortOrder:1,favoriteSourceDeviceId:'save-owner'};
 const saveUpdate={...reading,readingPositionChangedAt:50,sourceDeviceId:'old-reader',favoriteSaved:false,favoriteChangedAt:400,favoriteSourceDeviceId:'unsave-owner'};
 const recordCodec=new CloudRecordCodec(c), records=[];
 for(const [kind,value]of [['book',bookState],['book-reading',reading]] as const){const doc={kind,value};const recordId=await recordCodec.id(key,'offline-user',doc);records.push({recordId,envelope:await recordCodec.encode(key,'offline-user',recordId,1,doc)});}
 const value={records,encryptedManifestBase64:b64(carried.encryptedManifest),snapshotId:carried.manifest.snapshotId,manifestUtf8:cloudRaw(carried.manifest)?.source??JSON.stringify(carried.manifest),chunks,packageUtf8,privateBook,bookState,reading,saveUpdate,mergedReading:mergeBookReading(reading,saveUpdate),chapterHashes:hashes,articleIds:await Promise.all([1,2].map(n=>bookArticleId(id,n,sha))),microName,microBase64:b64(micro),overlayBase64:b64(overlay),settings,contentChunkSizes:contentChunks(Uint8Array.from({length:2200000},(_,i)=>i%251)).map(b=>b.length)};
 writeFileSync(path,JSON.stringify(value,null,2)+'\n');
 console.log('PodJS deterministic layout3 fixture generated');
}else if(mode==='verify'){
 const value=JSON.parse(readFileSync(path,'utf8'));
 if(value.records){const r=new CloudRecordCodec(c);for(const row of value.records)await r.decode(key,key,'offline-user',row);if(!value.snapshotId){console.log('PodJS decoded Kotlin book/book-reading records');process.exit(0);}}
 const m=await codec.decryptManifest(key,value.snapshotId,unb64(value.encryptedManifestBase64));
 const payload=await codec.restore(key,m,async h=>unb64(value.chunks[h]));
 for(const [name,b]of payload)if(name.startsWith('books/')){const s=async function*(){yield new TextDecoder().decode(b)};await validateBookPackage(splitBookLines(s()),sha,m.objects.find(o=>o.name===name)!.privateBook as any);}
 for(const o of m.objects)if(o.name.startsWith('private/articles/'))await validateMicroArchive(payload.get(o.name)!,value.articleId??'layout3-article',c);
 if(payload.has('private/overlay-v1.json'))await decodePrivateOverlay(payload.get('private/overlay-v1.json')!,sha);
 if(value.expectFuture && !cloudRaw(m)?.source.includes('9007199254740993123456789'))throw Error('raw unknown token lost');
 console.log('PodJS decoded Kotlin CM3/CC2, book/package, micro-WRSS and overlay; objects='+payload.size);
}else throw Error('mode must be generate or verify');
