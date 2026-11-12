const optionalBuffs = new Set(['realm', 'destiny', 'career', 'stance', 'bottleFirst', 'bottleSecond', 'experience', 'embryoGrowth']);
export type CardOutcome = { slot: number; cardId: number; values: number[] };

export function preparePlayer<T extends { buffs: Record<string, number | string>; talentIds: number[];
  cards: number[]; cardOutcomes?: CardOutcome[] }>(
  player: T,
) {
  const buffs: Record<string, number | string> = {};
  for (const [key, value] of Object.entries(player.buffs)) {
    if (value === '' && optionalBuffs.has(key)) continue;
    buffs[key] = value === '' ? 0 : value;
  }
  return { ...player, buffs, talentIds: player.talentIds.filter(id => id != null),
    cardOutcomes: player.cardOutcomes?.filter(entry => player.cards[entry.slot] === entry.cardId) };
}
