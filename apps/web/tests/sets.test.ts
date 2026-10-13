import {it,expect} from 'vitest';import {evaluateSets} from '../src/utils/sets';
const sets={A:['1','2','3'],B:['2','3','4'],C:['3']};
it('intersection, union and difference',()=>{expect(evaluateSets('A ∩ B',sets)).toEqual(['2','3']);expect(evaluateSets('A ∪ B',sets)).toEqual(['1','2','3','4']);expect(evaluateSets('A \\ B',sets)).toEqual(['1']);});
it('operators share left-to-right precedence',()=>expect(evaluateSets('A ∪ B ∩ C',sets)).toEqual(['3']));
it('parentheses override order',()=>expect(evaluateSets('A ∪ (B ∩ C)',sets)).toEqual(['1','2','3']));
it('malformed input and executable text never evaluate',()=>{for(const s of ['A ∩','(A ∪ B','A B','A ∪ Z','alert(1)','A);globalThis.marker=1'])expect(()=>evaluateSets(s,sets)).toThrow();});
