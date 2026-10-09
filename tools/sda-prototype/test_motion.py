"""Found lifecycle contracts with own textures and recovered numeric constants."""
import unittest
from motion import FoundMotion, native_round
from runtime import Scene
from test_selection import FixtureResources


class MotionTests(unittest.TestCase):
    def test_native_rounding_negative_remainder(self):
        self.assertEqual(native_round(2.5), 3)
        self.assertEqual(native_round(-2.7), -2)

    def test_delay_is_time_based_but_scale_step_is_per_update(self):
        quick = FoundMotion(10, 100, 8, 8)
        slow = FoundMotion(10, 100, 8, 8)
        quick.update(0.01)
        slow.update(0.2)
        self.assertEqual(quick.scale, slow.scale)
        self.assertEqual(quick.velocity, 0)
        self.assertNotEqual(quick.delay, slow.delay)

    def test_two_pulses_then_upward_retirement(self):
        motion = FoundMotion(20, 200, 8, 8)
        scales = []
        for _ in range(200):
            motion.update(0.04)
            scales.append(motion.scale)
            if motion.removed:
                break
        self.assertTrue(motion.removed)
        self.assertEqual(motion.pulses, 2)
        self.assertEqual(motion.phase, 0)
        self.assertEqual(motion.scale, 1)
        self.assertEqual(max(scales), 1.25)
        self.assertGreaterEqual(motion.velocity, -10)
        self.assertLess(motion.y, -motion.draw_height)
        before = (motion.x, motion.y, motion.delay)
        motion.update(1)
        self.assertEqual((motion.x, motion.y, motion.delay), before)

    def test_saved_rect_height_is_used_for_both_center_coordinates(self):
        motion = FoundMotion(10, 20, 20, 8)
        self.assertEqual((motion.center_x, motion.center_y), (14, 24))
        motion.update(0)
        self.assertLess(motion.x, 10)

    def test_visible_caption_and_saved_caption_use_different_states(self):
        scene = Scene(FixtureResources(), "SCENE_FIXTURE.MSL", [("a", "b")])
        scene.click(20, 20)
        self.assertEqual(scene.remaining_captions(), ["One sample"])
        self.assertEqual(scene.saved_captions(), ["Two samples"])
        for _ in range(200):
            scene.advance(0.04)
            if scene.objects["a"].motion.removed:
                break
        self.assertTrue(scene.objects["a"].motion.removed)
        self.assertEqual(scene.saved_captions(), ["One sample"])
        scene.click(30, 20)
        self.assertEqual(scene.remaining_captions(), [])
        self.assertEqual(scene.saved_captions(), ["One sample"])
        for _ in range(200):
            scene.advance(0.04)
        self.assertEqual(scene.saved_captions(), [])


if __name__ == "__main__":
    unittest.main()
