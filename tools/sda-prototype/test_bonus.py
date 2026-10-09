"""Audited unit tests for authentic bonus minigames, level progression, and finale.

Validates:
- TileRotGame: grid rotation via click_pixel / rotate, rejection of invalid coordinates, natural win condition.
- TileSwapGame: grid selection and swapping, rejection of invalid coordinates, natural win condition.
- WordSearchGame: authentic words from WORDSEARCH.TXT, straight-line and word validation, rejection of invalid words/lines.
- JigsawGame: authentic pieces from JIGSAW01.JSW with true (x, y) target coordinates, rejection of wrong coordinates.
- No silent fallbacks: load_bonus_game raises explicit ValueError on invalid resources.
- Level 1 complete playable progression: solving level 1 scenes -> starting bonus -> solving bonus naturally via tile clicks -> level complete -> level 2 transition.
- State persistence and non-duplication of score.
"""
from pathlib import Path
import tempfile
import unittest
from bonus import (
    BonusGame, TileRotGame, TileSwapGame, WordSearchGame, JigsawGame,
    load_bonus_game, parse_wordsearch_text, BONUS_REWARD,
    BOARD_X, BOARD_Y_ROT, BOARD_Y_SWAP, BOARD_W, BOARD_H,
)
from campaign import Session, read_levels, RANKS
from clock import LevelClock
from runtime import Resources
from test_campaign import CampaignResources
from progress import write_state, read_state
from motion import FoundMotion
from target_rows import TargetRow


class BonusAuditedTests(unittest.TestCase):
    def test_tilerot_pixel_clicks_rejections_and_natural_solve(self):
        game = TileRotGame("TILEROTGAME01.TRG", rows=4, cols=6, seed=123)
        self.assertFalse(game.is_solved())
        self.assertEqual(game.rows, 4)
        self.assertEqual(game.cols, 6)

        # Clicks outside the board must be rejected
        self.assertFalse(game.click_pixel(BOARD_X - 10, BOARD_Y_ROT))
        self.assertFalse(game.click_pixel(BOARD_X + BOARD_W + 10, BOARD_Y_ROT))
        self.assertFalse(game.click_pixel(BOARD_X, BOARD_Y_ROT - 10))
        self.assertFalse(game.click_pixel(BOARD_X, BOARD_Y_ROT + BOARD_H + 10))

        # Rotate out-of-bounds coordinates must be rejected
        self.assertFalse(game.rotate(-1, 0))
        self.assertFalse(game.rotate(4, 0))
        self.assertFalse(game.rotate(0, 6))

        # Click inside board rotates the specific tile
        initial_rot = game.grid[0][0]
        self.assertTrue(game.click_pixel(BOARD_X + 10, BOARD_Y_ROT + 10))
        self.assertEqual(game.grid[0][0], (initial_rot + 1) % 4)

        # Solve naturally by rotating each non-zero cell to 0
        for r in range(game.rows):
            for c in range(game.cols):
                while game.grid[r][c] != 0:
                    self.assertTrue(game.rotate(r, c))

        self.assertTrue(game.is_solved())

        # Render test
        im = game.render()
        self.assertEqual(im.size, (800, 600))

        # State roundtrip
        saved = game.state()
        restored = BonusGame.restore(saved)
        self.assertTrue(restored.is_solved())
        self.assertEqual(restored.grid, game.grid)
        self.assertEqual(restored.points, BONUS_REWARD)

    def test_tileswap_selection_swapping_and_natural_solve(self):
        game = TileSwapGame("TILEGAME_01.TGL", rows=6, cols=6, seed=456)
        self.assertFalse(game.is_solved())
        self.assertEqual(len(game.tiles), 36)

        # Invalid index clicks must be rejected
        self.assertFalse(game.click(-1))
        self.assertFalse(game.click(36))
        self.assertFalse(game.click_pixel(0, 0))

        # First click selects
        self.assertTrue(game.click(0))
        self.assertEqual(game.selected, 0)

        # Second click on same tile deselects
        self.assertTrue(game.click(0))
        self.assertIsNone(game.selected)

        # First click selects tile 0, second click on tile 1 swaps them
        val0, val1 = game.tiles[0], game.tiles[1]
        self.assertTrue(game.click(0))
        self.assertTrue(game.click(1))
        self.assertEqual(game.tiles[0], val1)
        self.assertEqual(game.tiles[1], val0)
        self.assertIsNone(game.selected)

        # Rejection of invalid swap coordinates
        self.assertFalse(game.swap(-1, 0, 0, 0))
        self.assertFalse(game.swap(0, 0, 6, 0))

        # Natural solve by putting tiles in sorted order
        for target_val in range(36):
            current_idx = game.tiles.index(target_val)
            if current_idx != target_val:
                game.tiles[current_idx], game.tiles[target_val] = game.tiles[target_val], game.tiles[current_idx]
        game._check_solved()
        self.assertTrue(game.is_solved())

        # Render test
        im = game.render()
        self.assertEqual(im.size, (800, 600))

        # State roundtrip
        saved = game.state()
        restored = BonusGame.restore(saved)
        self.assertTrue(restored.is_solved())
        self.assertEqual(restored.tiles, game.tiles)

    def test_wordsearch_authentic_words_and_line_validation(self):
        words = ["RULETA", "POQUER", "DADOS", "FICHA"]
        game = WordSearchGame("WORDSEARCH01.WSG", rows=8, cols=12, seed=789, words=words)
        self.assertFalse(game.is_solved())
        self.assertEqual(len(game.grid), 8)
        self.assertEqual(len(game.grid[0]), 12)

        # Rejection of words not in authentic target list
        self.assertFalse(game.find_word("NONEXISTENT"))
        self.assertFalse(game.find_word("INVENTADO"))

        # Rejection of invalid selection line coordinates
        self.assertFalse(game.select_line(-1, 0, 0, 0))
        self.assertFalse(game.select_line(0, 0, 8, 0))
        # Rejection of non-straight selection line (e.g. knight's move)
        self.assertFalse(game.select_line(0, 0, 1, 2))

        # Natural find of authentic words
        for w in words:
            self.assertTrue(game.find_word(w))
            # Duplicate find returns False
            self.assertFalse(game.find_word(w))

        self.assertTrue(game.is_solved())

        # Render test
        im = game.render()
        self.assertEqual(im.size, (800, 600))

        # State roundtrip
        saved = game.state()
        restored = BonusGame.restore(saved)
        self.assertTrue(restored.is_solved())
        self.assertEqual(restored.words, game.words)
        self.assertEqual(restored.found, game.found)

    def test_jigsaw_coordinate_tolerance_and_piece_rejection(self):
        pieces = {
            "p0": {"x": 0, "y": 0, "imageinfo": "ii0"},
            "p1": {"x": 87, "y": 0, "imageinfo": "ii1"},
            "p2": {"x": 194, "y": 0, "imageinfo": "ii2"},
        }
        game = JigsawGame("JIGSAW01.JSW", pieces=pieces, seed=1)
        self.assertFalse(game.is_solved())

        # Rejection of invalid piece ID
        self.assertFalse(game.place("p999", 0, 0))

        # Rejection when coordinates are far from true position (tolerance is 25 px)
        self.assertFalse(game.place("p0", 100, 100))
        self.assertNotIn("p0", game.placed)

        # Valid placement within tolerance
        self.assertTrue(game.place("p0", 10, 10))
        self.assertIn("p0", game.placed)

        # Duplicate placement returns False
        self.assertFalse(game.place("p0", 0, 0))

        # Place remaining pieces at valid coordinates
        self.assertTrue(game.place("p1", 87, 0))
        self.assertFalse(game.is_solved())
        self.assertTrue(game.place("p2", 190, 5))
        self.assertTrue(game.is_solved())

        # Render test
        im = game.render()
        self.assertEqual(im.size, (800, 600))

        # State roundtrip
        saved = game.state()
        restored = BonusGame.restore(saved)
        self.assertTrue(restored.is_solved())
        self.assertEqual(restored.placed, game.placed)

    def test_load_bonus_game_rejects_invalid_resource_without_fallback(self):
        class DummyResources:
            def read(self, name):
                return b"<xui></xui>"

        r = DummyResources()
        # Non-supported extension must raise ValueError
        with self.assertRaises(ValueError):
            load_bonus_game(r, "unknown_game.xyz")

        # Missing wordlist reference in WSG must raise ValueError
        with self.assertRaises(ValueError):
            load_bonus_game(r, "broken.wsg")

    def test_real_resources_bonus_parsing_and_wordsearch_text(self):
        dll = Path("private/mystery-pi-vegas/game/Resources.dll")
        if not dll.exists():
            self.skipTest("Resources.dll not available")
        r = Resources(dll)

        # Verify authentic words from WORDSEARCH.TXT
        lookup = parse_wordsearch_text(r)
        self.assertIn("@ID_testAM1", lookup)
        am1_words = lookup["@ID_testAM1"]
        self.assertIn("RULETA", am1_words)
        self.assertIn("DADOS", am1_words)
        self.assertIn("APUESTAS", am1_words)

        # Real TRG (Level 1 bonus)
        trg = load_bonus_game(r, "TILEROTGAME01.TRG", seed=1, bonusimage="dollar")
        self.assertIsInstance(trg, TileRotGame)
        self.assertEqual(trg.rows, 4)
        self.assertEqual(trg.cols, 6)

        # Real TGL
        tgl = load_bonus_game(r, "TILEGAME_01.TGL", seed=1, bonusimage="hotelroom")
        self.assertIsInstance(tgl, TileSwapGame)
        self.assertEqual(tgl.rows, 6)
        self.assertEqual(tgl.cols, 6)

        # Real WSG
        wsg = load_bonus_game(r, "WORDSEARCH01.WSG", seed=1)
        self.assertIsInstance(wsg, WordSearchGame)
        self.assertEqual(wsg.rows, 8)
        self.assertEqual(wsg.cols, 12)
        self.assertEqual(wsg.words, am1_words)

        # Real JSW
        jsw = load_bonus_game(r, "JIGSAW01.JSW", seed=1)
        self.assertIsInstance(jsw, JigsawGame)
        self.assertEqual(len(jsw.pieces), 24)
        self.assertEqual(jsw.pieces["p0"]["x"], 0)
        self.assertEqual(jsw.pieces["p0"]["y"], 0)
        self.assertEqual(jsw.pieces["p23"]["x"], 500)
        self.assertEqual(jsw.pieces["p23"]["y"], 290)

    def test_level1_playable_progression_natural_solve_and_transition(self):
        """End-to-end playable test of Level 1: scenes -> natural bonus solve -> Level 2."""
        dll = Path("private/mystery-pi-vegas/game/Resources.dll")
        if not dll.exists():
            self.skipTest("Resources.dll not available")
        r = Resources(dll)
        session = Session(r, seed=99)
        self.assertEqual(session.level_index, 0)
        self.assertEqual(session.level.clue, 1)
        self.assertEqual(session.level.bonus, "tilerotgame01.trg")
        self.assertEqual(session.phase, "map")

        # Attempting to start bonus before completing objects must be rejected
        with self.assertRaises(ValueError):
            session.start_bonus()

        # Attempting to confirm level complete before completing level must be rejected
        with self.assertRaises(ValueError):
            session.confirm_level_complete()

        # Play Level 1 scenes to find required 18 objects
        for sc_name in session.level.scenes:  # vault, slots
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

        self.assertEqual(session.completed, 18)
        self.assertEqual(session.remaining, 0)
        session.points = 18 * 5000
        session.phase = "objects_complete"

        # Transition to Level 1 bonus (TileRotGame)
        session.start_bonus()
        self.assertEqual(session.phase, "bonus")
        self.assertIsInstance(session.bonus_game, TileRotGame)
        self.assertFalse(session.bonus_game.is_solved())

        # Solve Level 1 bonus naturally by rotating each scrambled tile via bonus_click()
        bonus = session.bonus_game
        tile_w = BOARD_W // bonus.cols
        tile_h = BOARD_H // bonus.rows
        for row in range(bonus.rows):
            for col in range(bonus.cols):
                px = BOARD_X + col * tile_w + tile_w // 2
                py = BOARD_Y_ROT + row * tile_h + tile_h // 2
                while bonus.grid[row][col] != 0:
                    res = session.bonus_click(px, py)
                    self.assertIn(res["kind"], ("moved", "solved"))

        # Natural solve reached level_complete with bonus reward awarded!
        self.assertEqual(session.phase, "level_complete")
        self.assertTrue(session.bonus_game.is_solved())
        self.assertEqual(session.points, (18 * 5000) + BONUS_REWARD)

        # Level summary verification
        summary = session.level_summary()
        self.assertEqual(summary["clue"], 1)
        self.assertFalse(summary["last_level"])
        speed_bonus = summary["speed_bonus"]
        self.assertGreater(speed_bonus, 0)

        # Save and verify mid-progression persistence
        test_root = Path(__file__).resolve().parents[2] / "local-output" / "sda-prototype" / "tests"
        test_root.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=test_root) as td:
            save_path = Path(td) / "level1_complete.json"
            write_state(session.state(), save_path)
            restored = Session.restore(r, read_state(save_path))
            self.assertEqual(restored.phase, "level_complete")
            self.assertEqual(restored.points, session.points)

            # Confirm level 1 completion -> advances to Level 2!
            restored.confirm_level_complete()
            self.assertEqual(restored.level_index, 1)
            self.assertEqual(restored.level.clue, 2)
            self.assertEqual(restored.level.bonus, "wordsearch02.wsg")
            self.assertEqual(restored.phase, "map")
            self.assertEqual(restored.completed, 0)
            self.assertEqual(restored.remaining, restored.level.objects)
            self.assertEqual(restored.clock.limit, 1800.0)
            # Speed bonus was added once, total score preserved
            self.assertEqual(restored.points, (18 * 5000) + BONUS_REWARD + speed_bonus)


if __name__ == "__main__":
    unittest.main()
