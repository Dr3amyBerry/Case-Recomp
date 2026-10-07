from __future__ import annotations
import hashlib, json, struct, tempfile, unittest
from types import SimpleNamespace
from unittest.mock import patch
from pathlib import Path
from caserecomp.inspector import InspectionError
from caserecomp.__main__ import main
from caserecomp.vertical_slice import *

H1='1'*64; H2='2'*64; H3='3'*64; H4='4'*64; H5='5'*64; PACKAGE='9'*64

def digest(value):
    return hashlib.sha256(json.dumps(value,ensure_ascii=False,sort_keys=True,separators=(",",":")).encode()).hexdigest()

def spec():
    return {'format':SPEC_FORMAT,'version':1,'source_sha256':H1,'promotable_rules':0,
      'stages':[
       {'id':'boot','start_frame':1,'end_frame':9,'entry_sprite_count':1,'entry_behavior_count':0,'script_count':0,'entry_sprite_sha256':H2,'handler_set_sha256':H3},
       {'id':'menu','start_frame':10,'end_frame':19,'entry_sprite_count':2,'entry_behavior_count':1,'script_count':1,'entry_sprite_sha256':H3,'handler_set_sha256':H4},
       {'id':'map','start_frame':20,'end_frame':29,'entry_sprite_count':3,'entry_behavior_count':2,'script_count':2,'entry_sprite_sha256':H4,'handler_set_sha256':H5},
       {'id':'scene','start_frame':30,'end_frame':39,'entry_sprite_count':4,'entry_behavior_count':3,'script_count':3,'entry_sprite_sha256':H5,'handler_set_sha256':H2}],
      'transitions':[{'from':a,'to':b,'verification':'static-only'} for a,b in TRANSITIONS]}

def observation(kind='independent-original-runtime', controlled=True):
    s=spec(); doc={'format':OBS_FORMAT,'version':1,'source_sha256':H1,'evidence_kind':kind,'timing_trials':2,'controlled_timing':controlled,
      'stages':[{'id':x['id'],'frame':x['start_frame'],'sprite_sha256':x['entry_sprite_sha256'],'handler_set_sha256':x['handler_set_sha256'],'observable_state_sha256':H5} for x in s['stages']],
      'transitions':[{'from':'boot','to':'menu','input_kind':'none','elapsed_ms':0},{'from':'menu','to':'map','input_kind':'start','elapsed_ms':100 if controlled else 0},{'from':'map','to':'scene','input_kind':'enter-scene','elapsed_ms':150 if controlled else 0}]}
    if kind=='independent-original-runtime':
        doc['capture_evidence']={'format':'case-recomp-native-capture-consensus','version':1,'runtime_kind':'native-projector',
          'trial_count':2,'visual_consensus':True,'static_fingerprints_bound':True,
          'plan_sha256':digest(s),'trial_set_sha256':H3,
          'stage_pixel_sha256':{stage:H2 for stage in STAGES},
          'stage_dimensions':{stage:[8,6] for stage in STAGES},
          'transition_latency':[
            {'from':'boot','to':'menu','samples':2,'min_ms':20,'max_ms':21,'median_ms':20,'gate_verified':False},
            {'from':'menu','to':'map','samples':2,'min_ms':40,'max_ms':41,'median_ms':40,'gate_verified':controlled},
            {'from':'map','to':'scene','samples':2,'min_ms':45,'max_ms':46,'median_ms':45,'gate_verified':controlled}],
          'timing_tolerance_ms':16}
    return doc

def scenario():
    return {'format':'case-recomp-scenario','version':1,'id':'synthetic','design':{'width':320,'height':240},
            'scenes':[{'id':'room','frame_start':1,'frame_end':20,'targets':[{'id':'target','rect':[1,2,3,4],'z':1}]}], 'events':[]}

class VerticalSliceTests(unittest.TestCase):
    def test_private_label_decoder(self):
        block=b'menu\0map\0scene'; rows=[(10,0),(20,5),(30,9)]
        data=struct.pack('>H',3)+b''.join(struct.pack('>HH',*x) for x in rows)+struct.pack('>i',len(block))+block
        self.assertEqual(decode_private_frame_labels(data),{'menu':10,'map':20,'scene':30})
        with self.assertRaises(InspectionError): decode_private_frame_labels(data[:-2])

    def test_private_builder_with_synthetic_patched_archive(self):
        block=b'menu\0map\0scene\0next'; rows=[(10,0),(20,5),(30,9),(40,15)]
        labels=struct.pack('>H',4)+b''.join(struct.pack('>HH',*x) for x in rows)+struct.pack('>i',len(block))+block
        class Entry:
            def __init__(self,tag): self.tag=tag
        class Archive:
            kind='movie'; entries={1:Entry('VWSC'),2:Entry('VWLB'),3:Entry('CAS*'),4:Entry('CASt'),5:Entry('Lscr')}
            def get_resource(self,rid): return {1:b'score',2:labels,3:b'cast',4:b'member',5:b'script'}[rid]
        class Sprite:
            frame=1
            def signature(self): return ('sprite',)
        behavior=SimpleNamespace(start_frame=1,end_frame=39,cast_member=1,cast_lib=1)
        score=SimpleNamespace(frame_count=50,sprites=(Sprite(),),behaviors=(behavior,))
        member=SimpleNamespace(script_number=7)
        index={'handlers':[{'script_id':5,'name':H2,'bytecode_sha256':H3,'bytecode_size':4}]}
        with tempfile.TemporaryDirectory() as td:
            src=Path(td)/'movie.bin';src.write_bytes(b'owned')
            with patch('caserecomp.vertical_slice.open_archive',return_value=(Archive(),0)), patch('caserecomp.vertical_slice.parse_score',return_value=score), patch('caserecomp.vertical_slice.parse_cast_order',return_value=(4,)), patch('caserecomp.vertical_slice.parse_cast_member',return_value=member), patch('caserecomp.vertical_slice.lingo_script_number',return_value=7), patch('caserecomp.vertical_slice.index_archive',return_value=index):
                doc=build_private_vertical_slice(src,menu_label='menu',map_label='map',scene_label='scene')
                self.assertEqual([x['start_frame'] for x in doc['stages']],[1,10,20,30]); self.assertEqual(doc['stages'][-1]['end_frame'],39)
                self.assertEqual(doc['promotable_rules'],0); validate_private_slice(doc)

    def test_independent_comparison_can_produce_bound_flow_proof(self):
        result=compare_vertical_slice(spec(),observation())
        self.assertTrue(result['verified']); self.assertTrue(result['timing_verified'])
        proof=verified_flow_from_comparison(result,scenario=scenario(),scene_id='room',package_id=PACKAGE)
        self.assertEqual([x['not_before_ms'] for x in proof['rules']],[100,150])
        self.assertEqual(len(proof['scenario_sha256']),64); self.assertIs(validate_verified_flow_proof(proof),proof)

    def test_synthetic_evidence_never_promotes_original_rules(self):
        result=compare_vertical_slice(spec(),observation('synthetic-test'))
        self.assertFalse(result['verified'])
        with self.assertRaises(InspectionError): verified_flow_from_comparison(result,scenario=scenario(),scene_id='room',package_id=PACKAGE)

    def test_mismatch_semantics_and_uncontrolled_timing_fail_closed(self):
        bad=observation(); bad['stages'][2]['sprite_sha256']='0'*64
        self.assertFalse(compare_vertical_slice(spec(),bad)['verified'])
        bad=observation(); bad['transitions'][1]['input_kind']='none'
        with self.assertRaises(InspectionError): validate_slice_observation(bad)
        no_timing=compare_vertical_slice(spec(),observation(controlled=False))
        self.assertTrue(no_timing['verified']); self.assertFalse(no_timing['timing_verified'])
        self.assertEqual([x['not_before_ms'] for x in verified_flow_from_comparison(no_timing,scenario=scenario(),scene_id='room',package_id=PACKAGE)['rules']],[0,0])

    def test_cli_compare_and_flow_proof(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); sp=root/'spec.json'; ob=root/'obs.json'; sc=root/'scenario.json'; cmp=root/'cmp.json'; proof=root/'flow.crflow'
            sp.write_text(json.dumps(spec()),encoding='utf-8'); ob.write_text(json.dumps(observation()),encoding='utf-8'); sc.write_text(json.dumps(scenario()),encoding='utf-8')
            self.assertEqual(main(['slice-compare',str(sp),str(ob),'--output',str(cmp)]),0)
            self.assertEqual(main(['slice-flow-proof',str(cmp),str(sc),'--spec',str(sp),'--observation',str(ob),'--package-id',PACKAGE,'--scene-id','room','--output',str(proof)]),0)
            self.assertEqual(load_json(proof)['format'],FLOW_FORMAT)

    def test_tampered_comparison_and_wrong_plan_binding_fail_closed(self):
        s=spec(); ob=observation(); comparison=compare_vertical_slice(s,ob)
        tampered=json.loads(json.dumps(comparison)); tampered['transitions'][1]['elapsed_ms'] += 1
        with self.assertRaises(InspectionError):
            verified_flow_from_evidence(s,ob,tampered,scenario=scenario(),scene_id='room',package_id=PACKAGE)

        other=spec(); other['stages'][0]['end_frame']=8
        with self.assertRaises(InspectionError): compare_vertical_slice(other,ob)

        broken=observation(); broken['capture_evidence']['transition_latency'][1]['samples']=1
        with self.assertRaises(InspectionError): validate_slice_observation(broken)

    def test_flow_v2_requires_package_and_exact_evidence_chain(self):
        proof=verified_flow_from_comparison(
            compare_vertical_slice(spec(),observation()), scenario=scenario(), scene_id='room', package_id=PACKAGE,
        )
        self.assertEqual(proof['version'],FLOW_VERSION)
        self.assertEqual(proof['package_id'],PACKAGE)
        self.assertEqual(proof['binding_sha256'],digest({k:v for k,v in proof.items() if k!='binding_sha256'}))
        legacy=json.loads(json.dumps(proof)); legacy['version']=1
        with self.assertRaises(InspectionError): validate_verified_flow_proof(legacy)
        fractional_version=json.loads(json.dumps(proof)); fractional_version['version']=2.0
        fractional_version['binding_sha256']=digest({k:v for k,v in fractional_version.items() if k!='binding_sha256'})
        with self.assertRaises(InspectionError): validate_verified_flow_proof(fractional_version)
        missing=json.loads(json.dumps(proof)); del missing['evidence_chain']
        with self.assertRaises(InspectionError): validate_verified_flow_proof(missing)
        extra=json.loads(json.dumps(proof)); extra['unexpected']=True
        with self.assertRaises(InspectionError): validate_verified_flow_proof(extra)
        wrong=json.loads(json.dumps(proof)); wrong['rules'][0]['id']='other'
        with self.assertRaises(InspectionError): validate_verified_flow_proof(wrong)
        timing=json.loads(json.dumps(proof)); timing['rules'][0]['not_before_ms'] += 1
        with self.assertRaises(InspectionError): validate_verified_flow_proof(timing)
        overflow=json.loads(json.dumps(proof)); overflow['rules'][0]['not_before_ms']=9_223_372_036_854_775_808
        overflow['binding_sha256']=digest({k:v for k,v in overflow.items() if k!='binding_sha256'})
        with self.assertRaises(InspectionError): validate_verified_flow_proof(overflow)
        rebound=json.loads(json.dumps(timing)); rebound['binding_sha256']=digest({k:v for k,v in rebound.items() if k!='binding_sha256'})
        self.assertIs(validate_verified_flow_proof(rebound),rebound)
        with self.assertRaises(InspectionError):
            verified_flow_from_comparison(compare_vertical_slice(spec(),observation()),scenario=scenario(),scene_id='room',package_id='bad')

    def test_shared_python_android_v2_binding_vector(self):
        path=Path(__file__).parents[1]/'android/engine/src/test/resources/verified-flow-v2-python-vector.json'
        proof=json.loads(path.read_text(encoding='utf-8'))
        binding=proof.pop('binding_sha256')
        self.assertEqual('1f0645b60bb17ed028c3e97aa43de49f030ab9b1fa532a147de94377ca7bdd83',binding)
        self.assertEqual(binding,digest(proof))
        proof['binding_sha256']=binding
        self.assertIs(validate_verified_flow_proof(proof),proof)

    def test_validation_and_create_only(self):
        bad=spec(); bad['stages'][1]['start_frame']=1
        with self.assertRaises(InspectionError): validate_private_slice(bad)
        proof=verified_flow_from_comparison(compare_vertical_slice(spec(),observation()),scenario=scenario(),scene_id='room',package_id=PACKAGE)
        proof['evidence_kind']='synthetic-test'
        with self.assertRaises(InspectionError): validate_verified_flow_proof(proof)
        with tempfile.TemporaryDirectory() as td:
            path=Path(td)/'x.json'; write_json_create_only(path,scenario())
            with self.assertRaises(InspectionError): write_json_create_only(path,scenario())

if __name__=='__main__': unittest.main()
