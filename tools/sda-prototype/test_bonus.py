"""Unit tests for bonus games, level progression and campaign finale."""
from pathlib import Path
import unittest
from bonus import (
    BonusGame, TileRotGame, TileSwapGame, WordSearchGame, JigsawGame,
    load_bonus_game, BONUS_REWARD,
)
from campaign import Session, read_levels, RANKS
from clock import LevelClock
from runtime import Resources
from test_campaign import CampaignResources
from progress import write_state, read_state


class BonusAndProgressionTests(unittest.TestCase):
    def test_tilerot_rotation_solve_and_restore(self):
        game = TileRotGame("test.trg", rows=2, cols=3, seed=42)
        self.assertFalse(game.is_solved())
        # Manually rotate all cells to 0
        for r in range(game.rows):
            for c in range(game.cols):
                while game.grid[r][c] != 0:
                    game.rotate(r, c)
        self.assertTrue(game.is_solved())

        # Test solve method and restore
        game2 = TileRotGame("test.trg", rows=2, cols=3, seed=42)
        saved = game2.state()
        restored = BonusGame.restore(saved)
        self.assertEqual(restored.grid, game2.grid)
        self.assertFalse(restored.is_solved())
        restored.solve()
        self.assertTrue(restored.is_solved())
        self.assertEqual(restored.points, BONUS_REWARD)

    def test_tileswap_click_solve_and_restore(self):
        game = TileSwapGame("test.tgl", rows=2, cols=2, seed=10)
        self.assertFalse(game.is_solved())
        # Click first, then second to swap
        game.click(0)
        self.assertEqual(game.selected, 0)
        game.click(1)
        self.assertIsNone(game.selected)

        saved = game.state()
        restored = BonusGame.restore(saved)
        self.assertEqual(restored.tiles, game.tiles)
        restored.solve()
        self.assertTrue(restored.is_solved())

    def test_wordsearch_and_jigsaw(self):
        ws = WordSearchGame("test.wsg", words=["MPI", "VEGAS"])
        self.assertFalse(ws.is_solved())
        ws.find_word("mpi")
        self.assertFalse(ws.is_solved())
        ws.find_word("vegas")
        self.assertTrue(ws.is_solved())

        jig = JigsawGame("test.jsw", total_pieces=4)
        for i in range(3):
            jig.place_piece(i)
        self.assertFalse(jig.is_solved())
        jig.place_piece(3)
        self.assertTrue(jig.is_solved())

    def test_real_resource_bonus_parsing(self):
        dll = Path("private/mystery-pi-vegas/game/Resources.dll")
        if not dll.exists():
            self.skipTest("Resources.dll not available")
        r = Resources(dll)
        trg = load_bonus_game(r, "TILEROTGAME01.TRG", seed=1)
        self.assertIsInstance(trg, TileRotGame)
        self.assertEqual(trg.rows, 4)
        self.assertEqual(trg.cols, 6)

        tgl = load_bonus_game(r, "TILEGAME_01.TGL", seed=1)
        self.assertIsInstance(tgl, TileSwapGame)
        self.assertEqual(tgl.rows, 6)
        self.assertEqual(tgl.cols, 6)

        wsg = load_bonus_game(r, "WORDSEARCH01.WSG", seed=1)
        self.assertIsInstance(wsg, WordSearchGame)
        self.assertEqual(wsg.rows, 8)
        self.assertEqual(wsg.cols, 12)

        jsw = load_bonus_game(r, "JIGSAW01.JSW", seed=1)
        self.assertIsInstance(jsw, JigsawGame)
        self.assertEqual(jsw.total_pieces, 24)

    def test_level_progression_bonus_to_next_level(self):
        import tempfile
        class MultiLevelResources(CampaignResources):
            def read(self, name):
                if name == "LEVELS_1.XUI":
                    return (
                        b'<xui><mpi:levels>'
                        b'<mpi:level clue="1" time="1000" objects="2" scenes="one" levelname="L1" bonus="sample.trg"/>'
                        b'<mpi:level clue="2" time="1200" objects="2" scenes="two" levelname="L2" bonus="sample.trg"/>'
                        b'</mpi:levels></xui>'
                    )
                return super().read(name)

        with tempfile.TemporaryDirectory() as td:
            r = MultiLevelResources()
            r.path = Path(td) / "fixture.dat"
            r.path.write_bytes(b"multilevel fixture")
            session = Session(r, seed=7)
            self.assertEqual(session.level_index, 0)
            self.assertEqual(session.phase, "map")

            # Play scene one
            session.enter("one")
            for target in session.scene.targets[:2]:
                s = session.scene.objects[target]
                session.click(s.x + 1, s.y + 1)
            for _ in range(100):
                session.advance(.04)

            # Reached objects_complete
            self.assertEqual(session.remaining, 0)
            self.assertEqual(session.phase, "objects_complete")

            # Start bonus
            session.start_bonus()
            self.assertEqual(session.phase, "bonus")
            self.assertIsNotNone(session.bonus_game)
            self.assertFalse(session.bonus_game.is_solved())

            # Mid-bonus save and restore
            saved = session.state()
            restored = Session.restore(r, saved)
            self.assertEqual(restored.phase, "bonus")
            self.assertIsNotNone(restored.bonus_game)

            # Solve bonus
            before_pts = restored.points
            restored.solve_bonus()
            self.assertEqual(restored.phase, "level_complete")
            self.assertEqual(restored.points, before_pts + BONUS_REWARD)

            # Summary check
            summary = restored.level_summary()
            self.assertEqual(summary["clue"], 1)
            self.assertFalse(summary["last_level"])
            self.assertGreater(summary["speed_bonus"], 0)

            # Confirm level complete -> transitions to Level 2!
            restored.confirm_level_complete()
            self.assertEqual(restored.level_index, 1)
            self.assertEqual(restored.phase, "map")
            self.assertEqual(restored.level.clue, 2)
            self.assertEqual(restored.level.scenes, ("two",))
            self.assertEqual(restored.remaining, 2)
            self.assertEqual(restored.clock.limit, 1200)

    def test_final_level_reaches_campaign_complete(self):
        import tempfile
        class FinalLevelResources(CampaignResources):
            def read(self, name):
                if name == "LEVELS_1.XUI":
                    return (
                        b'<xui><mpi:levels>'
                        b'<mpi:level clue="25" time="3000" objects="2" scenes="one" levelname="Final" bonus="sample.trg"/>'
                        b'</mpi:levels></xui>'
                    )
                return super().read(name)

        with tempfile.TemporaryDirectory() as td:
            r = FinalLevelResources()
            r.path = Path(td) / "fixture.dat"
            r.path.write_bytes(b"final fixture")
            session = Session(r, seed=7)
            self.assertEqual(session.rank, RANKS[0])

            session.enter("one")
            for target in session.scene.targets[:2]:
                s = session.scene.objects[target]
                session.click(s.x + 1, s.y + 1)
            for _ in range(100):
                session.advance(.04)

            session.start_bonus()
            session.solve_bonus()
            self.assertEqual(session.phase, "level_complete")
            summary = session.level_summary()
            self.assertTrue(summary["last_level"])

            # Confirm finale -> campaign_complete!
            session.confirm_level_complete()
            self.assertEqual(session.phase, "campaign_complete")
            self.assertIsNone(session.current)

    def test_real_campaign_full_progression_all_25_levels(self):
        from motion import FoundMotion
        from target_rows import TargetRow
        dll = Path("private/mystery-pi-vegas/game/Resources.dll")
        if not dll.exists():
            self.skipTest("Resources.dll not available")
        r = Resources(dll)
        session = Session(r, seed=42)
        self.assertEqual(len(session.levels), 25)
        self.assertEqual(session.level_index, 0)
        self.assertEqual(session.phase, "map")

        # Progress through all 25 levels
        for idx in range(25):
            self.assertEqual(session.level_index, idx)
            self.assertEqual(session.level.clue, idx + 1)
            # Find required objects across scenes of the level
            for sc_name in session.level.scenes:
                if session.completed >= session.level.objects:
                    break
                sc = session.enter(sc_name)
                for grp in sc.active_sets:
                    if session.completed >= session.level.objects:
                        break
                    for item in grp:
                        obj = sc.objects[item]
                        obj.found = True
                        obj.motion = FoundMotion(obj.x, obj.y, obj.image.width, obj.image.height, removed=True)
                    session.counted[sc_name].add(grp)
                    session.completed += 1
                    if grp in sc.rows:
                        sc.rows[grp] = TargetRow(0.0, 0, True)
            session.clock.advance(10.0)
            session.points += session.level.objects * 5000
            session.phase = "objects_complete"

            # Transition to level bonus
            session.start_bonus()
            self.assertEqual(session.phase, "bonus")
            self.assertIsNotNone(session.bonus_game)
            self.assertIn(session.bonus_game.kind, ("tilerot", "tilegame", "wordsearch", "jigsaw"))

            # Solve bonus
            session.solve_bonus()
            self.assertEqual(session.phase, "level_complete")

            # Check level summary
            summary = session.level_summary()
            self.assertEqual(summary["clue"], idx + 1)
            if idx == 24:
                self.assertTrue(summary["last_level"])
            else:
                self.assertFalse(summary["last_level"])

            # Transition to next level (or finale)
            session.confirm_level_complete()

        # Reached campaign finale!
        self.assertEqual(session.phase, "campaign_complete")
        self.assertEqual(session.rank, "P.I. Maestro")
        self.assertGreater(session.points, 25 * BONUS_REWARD)
        self.assertGreater(session.total_elapsed, 200.0)

        # Verify state persistence of completed campaign
        import tempfile
        test_root = Path(__file__).resolve().parents[2] / "local-output" / "sda-prototype" / "tests"
        test_root.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=test_root) as td:
            save_path = Path(td) / "final_save.json"
            state = session.state()
            write_state(state, save_path)
            restored = Session.restore(r, read_state(save_path))
            self.assertEqual(restored.phase, "campaign_complete")
            self.assertEqual(restored.points, session.points)
            self.assertEqual(restored.rank, "P.I. Maestro")


if __name__ == "__main__":
    unittest.main()
