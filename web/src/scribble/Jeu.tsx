import { useState } from "react";
import { type Lang, texte } from "../i18n";
import type { Joueur } from "../relay/relay";
import { DUREE_DEFAUT, DUREES, lettres, masque, type Partie } from "./partie";

const REPONSE_MAX = 60;

export function Consigne({
  lang,
  partie,
  moi,
  estMeneur,
  restantMs,
  nbJoueurs,
  erreur,
  onLance,
  onChoisis,
}: {
  lang: Lang;
  partie: Partie | null;
  moi: string;
  estMeneur: boolean;
  restantMs: number;
  nbJoueurs: number;
  erreur: boolean;
  onLance: (duree: number) => void;
  onChoisis: (index: number) => void;
}) {
  const [duree, setDuree] = useState(DUREE_DEFAUT);

  if (!partie) {
    return (
      <div className="consigne">
        {estMeneur ? (
          <>
            <label className="reglage">
              {texte(lang, "duree")}
              <select
                value={duree}
                onChange={(e) => setDuree(Number(e.target.value))}
              >
                {DUREES.map((d) => (
                  <option key={d} value={d}>
                    {texte(lang, "secondes", { n: d })}
                  </option>
                ))}
              </select>
            </label>
            <button
              type="submit"
              disabled={nbJoueurs < 2}
              onClick={() => onLance(duree)}
            >
              {texte(lang, "lancer")}
            </button>
            {nbJoueurs < 2 && (
              <span className="aide">{texte(lang, "attenteJoueurs")}</span>
            )}
          </>
        ) : (
          <span className="aide">{texte(lang, "attenteMeneur")}</span>
        )}
        {erreur && <span className="erreur">{texte(lang, "erreurMots")}</span>}
      </div>
    );
  }

  const nom = partie.noms[partie.dessinateur] ?? "";
  const chrono =
    partie.phase === "dessin" || partie.phase === "choix" ? (
      <span className="chrono" aria-label={texte(lang, "chrono")}>
        {Math.ceil(restantMs / 1000)}
      </span>
    ) : null;

  if (partie.phase === "choix") {
    return (
      <div className="consigne">
        {partie.dessinateur === moi ? (
          <>
            <span>{texte(lang, "choisisMot")}</span>
            {partie.choix.map((mot, index) => (
              <button key={mot} onClick={() => onChoisis(index)}>
                {mot}
              </button>
            ))}
          </>
        ) : (
          <span className="aide">{texte(lang, "choisitMot", { nom })}</span>
        )}
        {chrono}
      </div>
    );
  }

  if (partie.phase === "dessin" && partie.mot) {
    const vu = partie.dessinateur === moi || partie.trouve.includes(moi);
    return (
      <div className="consigne">
        {partie.dessinateur === moi ? (
          <strong>{texte(lang, "dessine", { mot: partie.mot })}</strong>
        ) : vu ? (
          <strong>{texte(lang, "trouveMoi", { mot: partie.mot })}</strong>
        ) : (
          <>
            <span className="masque">{masque(partie.mot, partie.revele)}</span>
            <span className="aide">
              {texte(lang, "lettres", { n: lettres(partie.mot) })}
            </span>
          </>
        )}
        {chrono}
      </div>
    );
  }

  if (partie.phase === "bilan") {
    return (
      <div className="consigne">
        <strong>{texte(lang, "cetait", { mot: partie.mot ?? "" })}</strong>
      </div>
    );
  }

  return (
    <div className="consigne">
      <strong>{texte(lang, "recap")}</strong>
      {estMeneur ? (
        <button type="submit" onClick={() => onLance(partie.duree)}>
          {texte(lang, "rejouer")}
        </button>
      ) : (
        <span className="aide">{texte(lang, "recapAttente")}</span>
      )}
    </div>
  );
}

export function Devinette({
  lang,
  proche,
  onDevine,
}: {
  lang: Lang;
  proche: boolean;
  onDevine: (texte: string) => void;
}) {
  const [saisie, setSaisie] = useState("");
  return (
    <form
      className="devinette"
      onSubmit={(event) => {
        event.preventDefault();
        if (saisie.trim()) {
          onDevine(saisie);
          setSaisie("");
        }
      }}
    >
      <input
        aria-label={texte(lang, "reponse")}
        placeholder={texte(lang, "reponse")}
        maxLength={REPONSE_MAX}
        value={saisie}
        autoComplete="off"
        onChange={(e) => setSaisie(e.target.value)}
      />
      <button type="submit" disabled={!saisie.trim()}>
        {texte(lang, "proposer")}
      </button>
      {proche && <span className="proche">{texte(lang, "proche")}</span>}
    </form>
  );
}

export function Scores({
  lang,
  partie,
  joueurs,
  moi,
  meneur,
}: {
  lang: Lang;
  partie: Partie;
  joueurs: Joueur[];
  moi: string;
  meneur: string | null;
}) {
  const classes = [...joueurs].sort(
    (a, b) => (partie.scores[b.id] ?? 0) - (partie.scores[a.id] ?? 0),
  );
  return (
    <>
      <h2>{texte(lang, "scores")}</h2>
      <ol className="scores">
        {classes.map((j) => (
          <li key={j.id} className={j.id === moi ? "moi" : undefined}>
            <span>
              {j.name}
              {partie.phase !== "fin" && j.id === partie.dessinateur && (
                <span className="meneur"> · {texte(lang, "dessinateur")}</span>
              )}
              {j.id === meneur && (
                <span className="meneur"> · {texte(lang, "meneur")}</span>
              )}
              {partie.trouve.includes(j.id) && partie.phase !== "fin" && (
                <span className="trouve"> ✓</span>
              )}
            </span>
            <span className="points">
              {texte(lang, "points", { n: partie.scores[j.id] ?? 0 })}
            </span>
          </li>
        ))}
      </ol>
      {partie.fil.length > 0 && (
        <>
          <h2>{texte(lang, "reponses")}</h2>
          <ul className="fil">
            {partie.fil.map((ligne, index) => (
              <li key={index} className={ligne.trouve ? "trouve" : undefined}>
                {ligne.trouve
                  ? texte(lang, "aTrouve", {
                      nom: partie.noms[ligne.joueur] ?? "",
                    })
                  : texte(lang, "ligne", {
                      nom: partie.noms[ligne.joueur] ?? "",
                      texte: ligne.texte ?? "",
                    })}
              </li>
            ))}
          </ul>
        </>
      )}
    </>
  );
}
