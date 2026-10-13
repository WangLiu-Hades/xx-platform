const {chromium}=require('playwright');
const {expect}=require('playwright/test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const fixture=require('../src/mock/fixture.json');
const origin=process.env.PROTOTYPE_URL||'http://127.0.0.1:5182';
const output='audit/vue-migration/map-selection-results.json';

async function main(){
  const {fromLonLat}=await import('ol/proj.js');
  // Four nearby points and one visible point outside the selection rectangle.
  const db=structuredClone(fixture);
  db.devices=db.devices.slice(0,5).map((device,i)=>({...device,x:[46,54,46,54,85][i],y:[46,46,54,54,50][i]}));
  const browser=await chromium.launch({headless:true,executablePath:process.env.AUDIT_BROWSER||'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'});
  const context=await browser.newContext({viewport:{width:1600,height:1100}});
  await context.addInitScript(data=>localStorage.setItem('vue-prototype-mock-v2',JSON.stringify({version:2,db:data})),db);
  const page=await context.newPage(),checks=[],errors=[],consoleErrors=[];
  page.setDefaultTimeout(5000);
  page.on('pageerror',e=>errors.push(e.message));
  page.on('console',m=>{if(m.type()==='error')consoleErrors.push(m.text());});
  const selectedCard=()=>page.locator('aside .card').filter({has:page.getByRole('heading',{name:'已选设备',exact:true})});
  async function selected(indices){
    await expect(selectedCard().locator('article')).toHaveCount(indices.length);
    const actual=await selectedCard().locator('article button').allTextContents();
    assert.deepEqual(actual.map(s=>s.trim()).sort(),indices.map(i=>db.devices[i].name).sort());
    await expect(page.locator('.map-keyboard .el-checkbox.is-checked')).toHaveCount(indices.length);
  }
  async function drag(from,to){
    const canvas=page.locator('.map-canvas');await canvas.scrollIntoViewIfNeeded();
    const box=await canvas.boundingBox(),center={x:box.x+box.width/2,y:box.y+box.height/2};
    await page.mouse.move(center.x+from[0],center.y+from[1]);await page.mouse.down();
    await page.mouse.move(center.x+to[0],center.y+to[1],{steps:12});await page.mouse.up();
  }
  async function test(name,fn){await fn();checks.push({name,passed:true});console.log('PASS '+name);}
  try{
    await page.goto(origin);await page.getByRole('button',{name:'登录',exact:true}).click();await page.locator('.sidebar').waitFor();
    for(const name of ['地图研判','设备分布']){
      await page.locator('.sidebar').getByRole('button',{name,exact:true}).click();
      await expect(page.locator('.page-title h1')).toHaveText(name);
      await page.locator('.map-canvas canvas').first().waitFor();
      await page.getByRole('button',{name:'框选',exact:true}).click();
      await test(name+'：框内四点全部选中、框外点排除',async()=>{await drag([-110,-110],[110,110]);await selected([0,1,2,3]);});
      await test(name+'：反向拖拽选中同样四点',async()=>{await drag([110,110],[-110,-110]);await selected([0,1,2,3]);});
      await test(name+'：新框选替换为框内两点',async()=>{await drag([-65,10],[65,75]);await selected([0,1]);});
      await test(name+'：空白框选清空选择',async()=>{await drag([-110,-170],[-70,-135]);await selected([]);});
      if(name==='地图研判')await test('地图研判：切回单选仅选择点击的点位',async()=>{
        await page.getByRole('button',{name:'单选',exact:true}).click();
        const box=await page.locator('.map-canvas').boundingBox(),center=fromLonLat([116.45,39.9]),d=db.devices[0];
        const point=fromLonLat([116.2+d.x/200,39.65+d.y/200]),resolution=156543.03392804097/2**11;
        await page.mouse.click(box.x+box.width/2+(point[0]-center[0])/resolution,box.y+box.height/2-(point[1]-center[1])/resolution);
        await selected([0]);
      });
      await test(name+'：重置地图清空选择',async()=>{await page.getByRole('button',{name:'重置地图',exact:true}).click();await selected([]);});
    }
    await page.screenshot({path:'audit/vue-migration/map-selection.png',fullPage:true});
    assert.deepEqual(errors,[]);assert.deepEqual(consoleErrors,[]);
  }catch(e){checks.push({name:'地图选择回归',passed:false,error:String(e.stack||e)});await page.screenshot({path:'audit/vue-migration/map-selection-failure.png',fullPage:true});throw e;}
  finally{fs.writeFileSync(output,JSON.stringify({time:new Date().toISOString(),origin,checks,errors,consoleErrors},null,2));await browser.close();}
}
main().catch(e=>{console.error(e);process.exitCode=1;});
