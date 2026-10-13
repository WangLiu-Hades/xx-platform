import {request,ApiError} from './client';
import type {Response} from './client';
export type Task = Response<'getTask'>['data'];
export async function awaitTask(taskId:string,options:{signal:AbortSignal;intervalSeconds:number;onProgress?:(task:Task)=>void}){
 const interval=Math.max(1,options.intervalSeconds)*1000;
 while(!options.signal.aborted){const result=await request('getTask',{path:{taskId},signal:options.signal});const task=result.data;options.onProgress?.(task);if(task.status==='SUCCESS')return task;if(['FAILED','CANCELLED'].includes(task.status))throw new ApiError('任务终止：'+task.status,'TASK_'+task.status,task.traceId,409);await new Promise<void>((resolve,reject)=>{const timer=setTimeout(()=>{options.signal.removeEventListener('abort',abort);resolve();},interval);const abort=()=>{clearTimeout(timer);reject(new DOMException('任务轮询已取消','AbortError'));};options.signal.addEventListener('abort',abort,{once:true});});}
 throw new DOMException('任务轮询已取消','AbortError');
}
