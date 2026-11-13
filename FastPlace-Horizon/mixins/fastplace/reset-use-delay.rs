fn reset_use_delay(game: &mut crate::game::Game<'_>) {
    if crate::game::fastplace::enabled() {
        game.use_delay = 0;
    }
}
