from app.repositories.oracle_criteria import OracleCriteria


class FakeClock:
    def __init__(self):
        self.now = 0.0

    def __call__(self) -> float:
        return self.now


def _criteria(clock: FakeClock) -> tuple[OracleCriteria, list[int]]:
    calls: list[int] = []
    source = OracleCriteria(cache_seconds=300, clock=clock)

    def fake_query(sql: str, **params) -> list[tuple]:
        calls.append(1)
        return [(0, None, 6, 0)]

    source._query = fake_query
    return source, calls


def test_does_not_reread_db_within_cache_time():
    """캐시 시간 안에는 DB를 다시 읽지 않는다"""
    clock = FakeClock()
    source, calls = _criteria(clock)
    source.brackets(2026, "TAX_RATE")
    clock.now = 299
    source.brackets(2026, "TAX_RATE")
    assert len(calls) == 1


def test_rereads_db_after_cache_time():
    """캐시 시간이 지나면 DB에서 다시 읽는다"""
    clock = FakeClock()
    source, calls = _criteria(clock)
    source.brackets(2026, "TAX_RATE")
    clock.now = 300
    source.brackets(2026, "TAX_RATE")
    assert len(calls) == 2


def test_caches_separately_by_year_and_bracket_type():
    """연도와 구간 종류는 따로 캐시한다"""
    clock = FakeClock()
    source, calls = _criteria(clock)
    source.brackets(2026, "TAX_RATE")
    source.brackets(2025, "TAX_RATE")
    source.brackets(2026, "EARNED_CREDIT")
    assert len(calls) == 3
