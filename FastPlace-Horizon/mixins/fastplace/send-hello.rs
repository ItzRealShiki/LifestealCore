fn send_hello(game: &mut crate::game::Game<'_>) {
    use horizon_protocol::packets::{
        configuration::CustomPayload,
        play::Serverbound,
    };

    crate::game::fastplace::set_enabled(false);
    let packet = Serverbound::CustomPayload(CustomPayload {
        channel: "fastplace:hello".to_owned(),
        data: Vec::new(),
    });
    let _ = game.native_send_packet(packet);
}
