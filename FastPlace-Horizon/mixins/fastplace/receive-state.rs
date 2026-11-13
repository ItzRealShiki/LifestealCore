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
