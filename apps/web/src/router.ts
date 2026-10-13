import {createRouter,createWebHashHistory} from 'vue-router';
import {groups} from './domain';
export const pageNames=Object.values(groups).flat();
export const router=createRouter({history:createWebHashHistory(),routes:[{path:'/',redirect:'/page/0'},{path:'/page/:id',component:{template:'<span />'}},{path:'/:pathMatch(.*)*',redirect:'/page/0'}]});
