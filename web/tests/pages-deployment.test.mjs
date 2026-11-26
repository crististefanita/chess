import {assert, test} from './support/atf-node.mjs';
import {readFileSync} from 'node:fs';

const AsyncFunction = Object.getPrototypeOf(async function(){}).constructor;
const deploy = new AsyncFunction('github', 'core', 'context', 'process', 'setTimeout', readFileSync(process.env.CHESS_PAGES_SCRIPT, 'utf8'));
function fixture(overrides={}) {
    const calls=[], outputs=[], waits=[];
    const artifact={id:123, name:'github-pages', expired:false, workflow_run:{id:456}, ...overrides.artifact};
    const created={id:'deployment-1',page_url:'https://example.github.io/chess/',...overrides.created};
    const states=[...(overrides.states??['deployment_in_progress','succeed'])];
    const github={rest:{actions:{getArtifact:async input=>{calls.push(['artifact',input]);return {data:artifact}}}},
        request:async (route,input)=>{
            calls.push([route,input]);
            if(route==='POST /repos/{owner}/{repo}/pages/deployments') return {data:created};
            if(route.startsWith('GET ')) return {data:{status:states.length>1?states.shift():states[0]}};
            return {data:{}};
        }};
    const core={getIDToken:async()=>{calls.push(['OIDC']);return 'test-only-token'},setOutput:(...v)=>outputs.push(v)};
    const execute=()=>deploy(github,core,{repo:{owner:'example',repo:'chess'},runId:456,sha:'a'.repeat(40)},
        {env:{CHESS_PAGES_ARTIFACT_ID:overrides.receipt??'123'}},(callback,delay)=>{waits.push(delay);callback()});
    return {calls,outputs,waits,execute};
}

test('Pages deploys the exact current upload with OIDC and waits for confirmed success', async()=>{
    const f=fixture();await f.execute();
    assert.deepEqual(f.calls[0],['artifact',{owner:'example',repo:'chess',artifact_id:123}]);
    const post=f.calls.find(([route])=>route==='POST /repos/{owner}/{repo}/pages/deployments');
    assert.deepEqual(post[1],{owner:'example',repo:'chess',artifact_id:123,pages_build_version:'a'.repeat(40),oidc_token:'test-only-token'});
    assert.equal(f.calls.filter(([route])=>route.startsWith('GET ')).length,2);
    assert.deepEqual(f.waits,[5000]);
    assert.deepEqual(f.outputs,[['page_url','https://example.github.io/chess/']]);
    assert.equal(f.calls.some(([route])=>/list|cancel/.test(route)),false);
});

test('Missing foreign or expired upload receipts cannot create a deployment',async()=>{
    for(const option of [{receipt:'0'},{receipt:'abc'},{receipt:'9007199254740992'},
        {artifact:{id:999}},{artifact:{name:'another'}},{artifact:{expired:true}},{artifact:{workflow_run:{id:789}}}]) {
        const f=fixture(option);await assert.rejects(f.execute(),/receipt|artifact/);
        assert.equal(f.calls.some(([route])=>route==='OIDC'||route.startsWith('POST ')),false);
        assert.deepEqual(f.outputs,[]);
    }
});

test('Failure timeout and malformed deployment receipts never publish a success URL',async()=>{
    for(const option of [{states:['deployment_failed']},{states:['deployment_cancelled']},
        {states:['deployment_in_progress']},{created:{id:null}},{created:{page_url:''}}]) {
        const f=fixture(option);await assert.rejects(f.execute(),/deployment|Deployment|Pages/);
        assert.deepEqual(f.outputs,[]);
        if(!option.created) assert.equal(f.calls.filter(([route])=>route.endsWith('/cancel')).length,1);
        if(option.states?.[0]==='deployment_in_progress') {
            assert.equal(f.waits.length,120);
            assert.equal(f.calls.filter(([route])=>route.startsWith('GET ')).length,120);
        }
    }
});
