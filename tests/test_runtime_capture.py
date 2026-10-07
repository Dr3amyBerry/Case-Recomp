from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from PIL import Image

from caserecomp.__main__ import main
from caserecomp.inspector import InspectionError
from caserecomp.runtime_capture import *
from caserecomp.runtime_capture import _hash_file, _pixel_fingerprint
from caserecomp.vertical_slice import compare_vertical_slice, verified_flow_from_comparison

H1="1"*64; H2="2"*64; H3="3"*64; H4="4"*64; H5="5"*64

def plan(source_hash):
    return {"format":"case-recomp-private-vertical-slice","version":1,"source_sha256":source_hash,"promotable_rules":0,
      "stages":[
       {"id":"boot","start_frame":1,"end_frame":9,"entry_sprite_count":1,"entry_behavior_count":0,"script_count":0,"entry_sprite_sha256":H2,"handler_set_sha256":H3},
       {"id":"menu","start_frame":10,"end_frame":19,"entry_sprite_count":2,"entry_behavior_count":1,"script_count":1,"entry_sprite_sha256":H3,"handler_set_sha256":H4},
       {"id":"map","start_frame":20,"end_frame":29,"entry_sprite_count":3,"entry_behavior_count":2,"script_count":2,"entry_sprite_sha256":H4,"handler_set_sha256":H5},
       {"id":"scene","start_frame":30,"end_frame":39,"entry_sprite_count":4,"entry_behavior_count":3,"script_count":3,"entry_sprite_sha256":H5,"handler_set_sha256":H2}],
      "transitions":[{"from":"boot","to":"menu","verification":"static-only"},{"from":"menu","to":"map","verification":"static-only"},{"from":"map","to":"scene","verification":"static-only"}]}

def scenario():
    return {"format":"case-recomp-scenario","version":1,"id":"synthetic","design":{"width":80,"height":60},
            "scenes":[{"id":"room","frame_start":1,"frame_end":2,"targets":[{"id":"t","rect":[1,1,4,4],"z":1}]}],"events":[]}

class RuntimeCaptureTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(); self.root=Path(self.tmp.name)
        self.binary=self.root/"game.exe"; self.binary.write_bytes(b"native-projector")
        self.source=_hash_file(self.binary)
        self.plan=plan(self.source)
        self.shots=[]
        for index,color in enumerate(((10,20,30,255),(40,50,60,255),(70,80,90,255),(100,110,120,255))):
            path=self.root/f"shot-{index}.png"; Image.new("RGBA",(8,6),color).save(path); self.shots.append(path)
    def tearDown(self): self.tmp.cleanup()

    def input(self, trial="one", offset=0, probes=True):
        transitions=[
          {"from":"boot","to":"menu","input_kind":"none","input_at_ms":0+offset,"visible_at_ms":20+offset},
          {"from":"menu","to":"map","input_kind":"start","input_at_ms":100+offset,"visible_at_ms":140+offset},
          {"from":"map","to":"scene","input_kind":"enter-scene","input_at_ms":200+offset,"visible_at_ms":245+offset},
        ]
        if probes:
            transitions[1]["gate_probe"]={"rejected_before_ms":9,"accepted_at_ms":10}
            transitions[2]["gate_probe"]={"rejected_before_ms":19,"accepted_at_ms":20}
        return {"format":CAPTURE_INPUT_FORMAT,"version":1,"runtime_kind":"native-projector","trial_id":trial,"controlled":True,
                "stages":[{"id":id,"frame":frame,"screenshot":str(path),"marker_observed":id!="boot"}
                          for id,frame,path in zip(("boot","menu","map","scene"),(1,10,20,30),self.shots)],
                "transitions":transitions}

    def test_trial_strips_paths_and_consensus_promotes(self):
        a=build_capture_trial(self.plan,self.input("one"),self.binary)
        b=build_capture_trial(self.plan,self.input("two",50),self.binary)
        self.assertNotIn(str(self.root),json.dumps(a)); self.assertEqual(a["runtime_kind"],"native-projector")
        observation=finalize_capture_trials(self.plan,[a,b])
        self.assertTrue(observation["capture_evidence"]["visual_consensus"])
        self.assertTrue(observation["controlled_timing"])
        comparison=compare_vertical_slice(self.plan,observation)
        self.assertTrue(comparison["verified"]); self.assertTrue(comparison["timing_verified"])
        proof=verified_flow_from_comparison(comparison,scenario=scenario(),scene_id="room")
        self.assertEqual([x["not_before_ms"] for x in proof["rules"]],[10,20])

    def test_without_gate_probe_navigation_can_verify_but_timing_stays_zero(self):
        a=build_capture_trial(self.plan,self.input("one",probes=False),self.binary)
        b=build_capture_trial(self.plan,self.input("two",40,probes=False),self.binary)
        observation=finalize_capture_trials(self.plan,[a,b])
        self.assertFalse(observation["controlled_timing"])
        comparison=compare_vertical_slice(self.plan,observation)
        self.assertTrue(comparison["verified"]); self.assertFalse(comparison["timing_verified"])
        proof=verified_flow_from_comparison(comparison,scenario=scenario(),scene_id="room")
        self.assertEqual([x["not_before_ms"] for x in proof["rules"]],[0,0])

    def test_visual_or_source_mismatch_fails_closed(self):
        a=build_capture_trial(self.plan,self.input("one"),self.binary)
        Image.new("RGBA",(8,6),(255,0,0,255)).save(self.shots[2])
        b=build_capture_trial(self.plan,self.input("two"),self.binary)
        with self.assertRaises(InspectionError): finalize_capture_trials(self.plan,[a,b])
        other=self.root/"other.exe"; other.write_bytes(b"other")
        with self.assertRaises(InspectionError): build_capture_trial(self.plan,self.input("bad"),other)

    def test_input_validation_and_png_fingerprint(self):
        bad=self.input(); bad["stages"][1]["marker_observed"]=False
        with self.assertRaises(InspectionError): build_capture_trial(self.plan,bad,self.binary)
        bad=self.input(); bad["transitions"][1]["visible_at_ms"]=1
        with self.assertRaises(InspectionError): build_capture_trial(self.plan,bad,self.binary)
        jpg=self.root/"bad.png"; Image.new("RGB",(2,2)).save(jpg,format="JPEG")
        with self.assertRaises(InspectionError): _pixel_fingerprint(jpg)

    def test_capture_desktop_uses_imagegrab_and_create_only(self):
        out=self.root/"desktop.png"
        image=Image.new("RGB",(4,3),(1,2,3))
        with patch("PIL.ImageGrab.grab",return_value=image):
            row=capture_desktop_png(out,(0,0,4,3))
        self.assertEqual((row["width"],row["height"]),(4,3))
        with self.assertRaises(InspectionError): capture_desktop_png(out)

    def test_cli_trial_finalize_and_compare(self):
        plan_path=self.root/"plan.json"; plan_path.write_text(json.dumps(self.plan),encoding="utf-8")
        inputs=[]; trials=[]
        for n in range(2):
            inp=self.root/f"input-{n}.json"; inp.write_text(json.dumps(self.input(str(n),n*40)),encoding="utf-8"); inputs.append(inp)
            trial=self.root/f"trial-{n}.json"; trials.append(trial)
            self.assertEqual(main(["slice-capture-trial",str(plan_path),str(inp),"--runtime-binary",str(self.binary),"--output",str(trial)]),0)
        obs=self.root/"observation.json"
        self.assertEqual(main(["slice-capture-finalize",str(plan_path),*[str(x) for x in trials],"--output",str(obs)]),0)
        self.assertEqual(json.loads(obs.read_text())["evidence_kind"],"independent-original-runtime")

if __name__=="__main__": unittest.main()
