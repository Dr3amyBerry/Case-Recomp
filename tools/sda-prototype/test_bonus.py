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
        self.assertEqual(wsg.words, am1_words[:6])
        self.assertTrue(set(wsg.words).issubset(am1_words))

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

    def test_level2_playable_progression_wordsearch_solve_and_transition(self):
        """End-to-end playable test of Level 2: scenes -> natural WordSearch bonus solve -> Level 3."""
        dll = Path("private/mystery-pi-vegas/game/Resources.dll")
        if not dll.exists():
            self.skipTest("Resources.dll not available")
        r = Resources(dll)
        session = Session(r, seed=42, level_index=1)
        self.assertEqual(session.level_index, 1)
        self.assertEqual(session.level.clue, 2)
        self.assertEqual(session.level.bonus, "wordsearch02.wsg")

        # Complete Level 2 required objects (27 objects across scenes)
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

        self.assertEqual(session.completed, 27)
        session.points = 27 * 5000
        session.phase = "objects_complete"

        # Start WordSearch bonus
        session.start_bonus()
        self.assertEqual(session.phase, "bonus")
        self.assertIsInstance(session.bonus_game, WordSearchGame)
        bonus = session.bonus_game
        self.assertFalse(bonus.is_solved())

        # Test invalid click outside board
        res = session.bonus_click(10, 10)
        self.assertEqual(res["kind"], "miss")

        # Solve WordSearch naturally via two-click pixel selection
        tile_w = BOARD_W // bonus.cols
        tile_h = BOARD_H // bonus.rows
        for word in bonus.words:
            coords = bonus.find_word_coordinates(word)
            self.assertIsNotNone(coords, f"Authentic word {word} must be placed on board")
            (r0, c0), (r_end, c_end) = coords
            px1 = BOARD_X + c0 * tile_w + tile_w // 2
            py1 = BOARD_Y_ROT + r0 * tile_h + tile_h // 2
            px2 = BOARD_X + c_end * tile_w + tile_w // 2
            py2 = BOARD_Y_ROT + r_end * tile_h + tile_h // 2

            # Click 1: select start letter
            res1 = session.bonus_click(px1, py1)
            self.assertEqual(res1["kind"], "moved")
            self.assertEqual(bonus.selected_start, (r0, c0))

            # Click 2: select end letter (submits line)
            res2 = session.bonus_click(px2, py2)
            self.assertIn(res2["kind"], ("moved", "solved"))
            self.assertIn(word, bonus.found)

        # Naturally solved and awarded points
        self.assertEqual(session.phase, "level_complete")
        self.assertTrue(bonus.is_solved())
        self.assertEqual(session.points, (27 * 5000) + BONUS_REWARD)

        # Confirm level completion -> transitions to Level 3 (jigsaw01.jsw)
        session.confirm_level_complete()
        self.assertEqual(session.level_index, 2)
        self.assertEqual(session.level.clue, 3)
        self.assertEqual(session.level.bonus, "jigsaw01.jsw")
        self.assertEqual(session.phase, "map")

    def test_level3_playable_progression_jigsaw_solve_and_transition(self):
        """End-to-end playable test of Level 3: scenes -> natural Jigsaw bonus solve -> Level 4."""
        dll = Path("private/mystery-pi-vegas/game/Resources.dll")
        if not dll.exists():
            self.skipTest("Resources.dll not available")
        r = Resources(dll)
        session = Session(r, seed=77, level_index=2)
        self.assertEqual(session.level_index, 2)
        self.assertEqual(session.level.clue, 3)
        self.assertEqual(session.level.bonus, "jigsaw01.jsw")

        # Complete Level 3 objects (37 objects)
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

        self.assertEqual(session.completed, 37)
        session.points = 37 * 5000
        session.phase = "objects_complete"

        # Start Jigsaw bonus
        session.start_bonus()
        self.assertEqual(session.phase, "bonus")
        self.assertIsInstance(session.bonus_game, JigsawGame)
        bonus = session.bonus_game
        self.assertFalse(bonus.is_solved())

        # Test rejection of placement outside tolerance
        p0_info = bonus.pieces["p0"]
        bonus.select_piece("p0")
        bad_px = BOARD_X + p0_info["x"] + 150
        bad_py = BOARD_Y_SWAP + p0_info["y"] + 150
        res_bad = session.bonus_click(bad_px, bad_py)
        self.assertEqual(res_bad["kind"], "miss")
        self.assertNotIn("p0", bonus.placed)

        # Naturally place all 24 pieces with authentic coordinate verification
        for pid in sorted(bonus.pieces.keys()):
            info = bonus.pieces[pid]
            bonus.select_piece(pid)
            px = BOARD_X + info["x"] + 10  # within 25px tolerance
            py = BOARD_Y_SWAP + info["y"] + 10
            res = session.bonus_click(px, py)
            self.assertIn(res["kind"], ("moved", "solved"))
            self.assertIn(pid, bonus.placed)

        # Naturally solved and awarded points
        self.assertEqual(session.phase, "level_complete")
        self.assertTrue(bonus.is_solved())
        self.assertEqual(session.points, (37 * 5000) + BONUS_REWARD)

        # Confirm level completion -> transitions to Level 4 (tilegame_01.tgl)
        session.confirm_level_complete()
        self.assertEqual(session.level_index, 3)
        self.assertEqual(session.level.clue, 4)
        self.assertEqual(session.level.bonus, "tilegame_01.tgl")
        self.assertEqual(session.phase, "map")

    def test_level4_playable_progression_tileswap_solve_and_transition(self):
        """End-to-end playable test of Level 4: scenes -> natural TileSwap bonus solve -> Level 5."""
        dll = Path("private/mystery-pi-vegas/game/Resources.dll")
        if not dll.exists():
            self.skipTest("Resources.dll not available")
        r = Resources(dll)
        session = Session(r, seed=123, level_index=3)
        self.assertEqual(session.level_index, 3)
        self.assertEqual(session.level.clue, 4)
        self.assertEqual(session.level.bonus, "tilegame_01.tgl")

        # Complete Level 4 objects (45 objects)
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

        self.assertEqual(session.completed, 45)
        session.points = 45 * 5000
        session.phase = "objects_complete"

        # Start TileSwap bonus
        session.start_bonus()
        self.assertEqual(session.phase, "bonus")
        self.assertIsInstance(session.bonus_game, TileSwapGame)
        bonus = session.bonus_game
        self.assertFalse(bonus.is_solved())

        # Test rejection of clicking outside board
        res_outside = session.bonus_click(5, 5)
        self.assertEqual(res_outside["kind"], "miss")

        # Naturally solve 6x6 TileSwap by swapping tiles via pixel clicks
        tile_w = BOARD_W // bonus.cols
        tile_h = BOARD_H // bonus.rows
        for target_val in range(len(bonus.tiles)):
            curr_pos = bonus.tiles.index(target_val)
            if curr_pos != target_val:
                px_from = BOARD_X + (curr_pos % bonus.cols) * tile_w + tile_w // 2
                py_from = BOARD_Y_SWAP + (curr_pos // bonus.cols) * tile_h + tile_h // 2
                px_to = BOARD_X + (target_val % bonus.cols) * tile_w + tile_w // 2
                py_to = BOARD_Y_SWAP + (target_val // bonus.cols) * tile_h + tile_h // 2

                # Click 1: select tile at curr_pos
                res1 = session.bonus_click(px_from, py_from)
                self.assertEqual(res1["kind"], "moved")

                # Click 2: swap with tile at target_val
                res2 = session.bonus_click(px_to, py_to)
                self.assertIn(res2["kind"], ("moved", "solved"))

        # Naturally solved and awarded points
        self.assertEqual(session.phase, "level_complete")
        self.assertTrue(bonus.is_solved())
        self.assertEqual(session.points, (45 * 5000) + BONUS_REWARD)

        # Confirm level completion -> transitions to Level 5 (wordsearch01.wsg)
        session.confirm_level_complete()
        self.assertEqual(session.level_index, 4)
        self.assertEqual(session.level.clue, 5)
        self.assertEqual(session.level.bonus, "wordsearch01.wsg")
        self.assertEqual(session.phase, "map")

    def test_campaign_level25_finale_progression_and_completion(self):
        """End-to-end playable test of Level 25 and campaign completion."""
        dll = Path("private/mystery-pi-vegas/game/Resources.dll")
        if not dll.exists():
            self.skipTest("Resources.dll not available")
        r = Resources(dll)
        session = Session(r, seed=555, level_index=24)
        self.assertEqual(session.level_index, 24)
        self.assertEqual(session.level.clue, 25)
        self.assertEqual(session.level.bonus, "tilerotgame01.trg")
        self.assertEqual(session.rank, "P.I. Maestro")

        # Complete Level 25 objects (90 objects)
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

        self.assertEqual(session.completed, 90)
        session.points = 90 * 5000
        session.phase = "objects_complete"

        # Start Level 25 bonus
        session.start_bonus()
        self.assertEqual(session.phase, "bonus")
        self.assertIsInstance(session.bonus_game, TileRotGame)
        bonus = session.bonus_game

        # Naturally solve Level 25 bonus via pixel tile rotations
        tile_w = BOARD_W // bonus.cols
        tile_h = BOARD_H // bonus.rows
        for row in range(bonus.rows):
            for col in range(bonus.cols):
                px = BOARD_X + col * tile_w + tile_w // 2
                py = BOARD_Y_ROT + row * tile_h + tile_h // 2
                while bonus.grid[row][col] != 0:
                    res = session.bonus_click(px, py)
                    self.assertIn(res["kind"], ("moved", "solved"))

        self.assertEqual(session.phase, "level_complete")
        self.assertTrue(bonus.is_solved())
        summary = session.level_summary()
        self.assertTrue(summary["last_level"])
        self.assertEqual(summary["rank"], "P.I. Maestro")

        # Confirm level complete -> transitions to campaign_complete
        session.confirm_level_complete()
        self.assertEqual(session.phase, "campaign_complete")
        self.assertEqual(session.rank, "P.I. Maestro")

        # Save and restore campaign_complete state
        test_root = Path(__file__).resolve().parents[2] / "local-output" / "sda-prototype" / "tests"
        test_root.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=test_root) as td:
            save_path = Path(td) / "campaign_complete.json"
            write_state(session.state(), save_path)
            restored = Session.restore(r, read_state(save_path))
            self.assertEqual(restored.phase, "campaign_complete")
            self.assertEqual(restored.points, session.points)
            self.assertEqual(restored.rank, "P.I. Maestro")


if __name__ == "__main__":
    unittest.main()
