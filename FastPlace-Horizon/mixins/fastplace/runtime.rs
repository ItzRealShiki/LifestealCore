use std::sync::atomic::{AtomicBool, Ordering};

static ENABLED: AtomicBool = AtomicBool::new(false);

pub fn set_enabled(enabled: bool) {
    ENABLED.store(enabled, Ordering::Relaxed);
}

pub fn enabled() -> bool {
    ENABLED.load(Ordering::Relaxed)
}

pub fn decode_state(data: &[u8]) -> bool {
    data.first().copied() == Some(1)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn state_is_disabled_by_default_and_can_be_changed() {
        set_enabled(false);
        assert!(!enabled());
        set_enabled(true);
        assert!(enabled());
        set_enabled(false);
        assert!(!enabled());
    }

    #[test]
    fn state_payload_uses_one_boolean_byte() {
        assert!(decode_state(&[1]));
        assert!(!decode_state(&[0]));
        assert!(!decode_state(&[]));
        assert!(!decode_state(&[2]));
    }
}
