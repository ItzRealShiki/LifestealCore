fn reset_use_delay(game: &mut crate::game::Game<'_>) {
    if crate::game::fastplace::enabled() {
        game.use_delay = 0;
    }
}

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

fn receive_state(packet: &horizon_protocol::packets::play::Clientbound) {
    use horizon_protocol::packets::play::Clientbound;

    if let Clientbound::CustomPayload(payload) = packet {
        if payload.channel == "fastplace:state" {
            crate::game::fastplace::set_enabled(
                crate::game::fastplace::decode_state(&payload.data),
            );
        }
    }
}

fn disconnect_reset() {
    crate::game::fastplace::set_enabled(false);
}
