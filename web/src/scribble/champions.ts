import type { Lang } from "../i18n";

// Data Dragon sert les noms traduits ; seuls les champions pour l'instant (réponse à Schub#130).
const DDRAGON = "https://ddragon.leagueoflegends.com";
const LOCALES: Record<Lang, string> = { fr: "fr_FR", en: "en_US" };
const lus = new Map<Lang, Promise<string[]>>();

const lis = async (lang: Lang): Promise<string[]> => {
  const versions = (await (
    await fetch(`${DDRAGON}/api/versions.json`)
  ).json()) as string[];
  const catalogue = (await (
    await fetch(
      `${DDRAGON}/cdn/${versions[0]}/data/${LOCALES[lang]}/champion.json`,
    )
  ).json()) as { data: Record<string, { name: string }> };
  return Object.values(catalogue.data).map((champion) => champion.name);
};

export const champions = (lang: Lang): Promise<string[]> => {
  if (!lus.has(lang)) {
    lus.set(
      lang,
      lis(lang).catch((erreur: unknown) => {
        lus.delete(lang);
        throw erreur;
      }),
    );
  }
  return lus.get(lang)!;
};
