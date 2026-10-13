export function evaluateSets(expression:string,sets:Record<string,string[]>):string[]{
 const tokens=expression.match(/[A-Z]|[∩∪\\()\-]/g)||[];
 if(tokens.join('')!==expression.replace(/\s/g,'')||!tokens.length)throw new Error('表达式只能包含 A–Z、∩、∪、\\、- 和括号');
 let offset=0;
 function atom():Set<string>{const t=tokens[offset++];if(t==='('){const result=sequence();if(tokens[offset++]!==')')throw new Error('括号未闭合');return result;}if(!t||!sets[t])throw new Error('缺少操作数 '+(t||''));return new Set(sets[t]);}
 function sequence():Set<string>{let result=atom();while(offset<tokens.length&&tokens[offset]!==')'){const op=tokens[offset++];if(!['∩','∪','\\','-'].includes(op||''))throw new Error('缺少运算符');const right=atom();result=op==='∩'?new Set([...result].filter(v=>right.has(v))):op==='∪'?new Set([...result,...right]):new Set([...result].filter(v=>!right.has(v)));}return result;}
 const result=sequence();if(offset!==tokens.length)throw new Error('多余括号');return [...result];
}
