import { useCallback, useEffect, useRef, useState } from "react";
import { annonceSalon, attendInit, dansUnHote } from "../host/bridge";
import { type Cle, langueDuNavigateur, type Lang, texte } from "../i18n";
import { playerId, playerName, rememberName } from "../identity";
import { type Evenement, type Joueur, Relais } from "../relay/relay";
import { Board } from "./Board";
import { Flux } from "./flux";
import { Consigne, Devinette, Scores } from "./Jeu";
import { estMorceau, type Morceau, Tableau } from "./traits";
import { estMessageJeu, type MessageJeu, usePartie } from "./usePartie";

const COULEURS = [
  "#F2E9EE",
  "#C9A227",
  "#A4477E",
  "#5B9BE8",
  "#4FB783",
  "#E0576B",
];
const EPAISSEURS = [0.004, 0.009, 0.02];
const NOM_MAX = 24;

// Le salon demandé par l'adresse : scribble.premadelab.eu/ABCD.
const salonDeLAdresse = (): string | null => {
  const code = location.pathname.slice(1);
  return /^[A-Z]{4}$/.test(code) ? code : null;
};

interface Contexte {
  lang: Lang;
  room: string | null;
  hote: string | null;
}

export function ScribbleApp() {
  const [contexte, setContexte] = useState<Contexte | null>(null);
  const [nom, setNom] = useState<string | null>(playerName());

  useEffect(() => {
    if (!dansUnHote()) {
      setContexte({
        lang: langueDuNavigateur(),
        room: salonDeLAdresse(),
        hote: null,
      });
      return;
    }
    void attendInit().then((init) =>
      setContexte({ lang: init.lang, room: init.room, hote: init.origin }),
    );
  }, []);

  if (!contexte) {
    return null;
  }
  if (!nom) {
    return (
      <Pseudo lang={contexte.lang} onChoisi={(choisi) => setNom(choisi)} />
    );
  }
  return <Salon contexte={contexte} nom={nom} />;
}

function Pseudo({
  lang,
  onChoisi,
}: {
  lang: Lang;
  onChoisi: (nom: string) => void;
}) {
  const [saisie, setSaisie] = useState("");
  const propre = saisie.trim().slice(0, NOM_MAX);
  return (
    <main className="accueil">
      <h1>{texte(lang, "titre")}</h1>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          if (propre) {
            rememberName(propre);
            onChoisi(propre);
          }
        }}
      >
        <label htmlFor="pseudo">{texte(lang, "pseudoInvite")}</label>
        <input
          id="pseudo"
          autoFocus
          maxLength={NOM_MAX}
          value={saisie}
          onChange={(e) => setSaisie(e.target.value)}
        />
        <button type="submit" disabled={!propre}>
          {texte(lang, "rejoindre")}
        </button>
      </form>
    </main>
  );
}

function Salon({ contexte, nom }: { contexte: Contexte; nom: string }) {
  const { lang } = contexte;
  const moi = useRef(playerId()).current;
  const tableau = useRef(new Tableau()).current;
  const recus = useRef(new Flux<Morceau>()).current;
  const relais = useRef<Relais | null>(null);
  const [demande, setDemande] = useState<string | null>(contexte.room);
  const [room, setRoom] = useState<string | null>(null);
  const [joueurs, setJoueurs] = useState<Joueur[]>([]);
  const [meneur, setMeneur] = useState<string | null>(null);
  const [version, setVersion] = useState(0);
  const [etat, setEtat] = useState<
    "connexion" | "salon" | "reconnexion" | "erreur"
  >("connexion");
  const [erreur, setErreur] = useState<Cle | null>(null);
  const [couleur, setCouleur] = useState(COULEURS[0]);
  const [epaisseur, setEpaisseur] = useState(EPAISSEURS[1]);
  const [copie, setCopie] = useState(false);
  const [maintenant, setMaintenant] = useState(() => Date.now());

  const envoieJeu = useCallback(
    (message: MessageJeu) => relais.current?.envoie(message, false),
    [],
  );
  const effaceTout = useCallback(() => {
    relais.current?.efface();
    tableau.vide();
    setVersion((v) => v + 1);
  }, [tableau]);
  const jeu = usePartie({
    moi,
    meneur,
    joueurs,
    lang,
    envoie: envoieJeu,
    efface: effaceTout,
  });
  // L'écoute du relais reste stable : la changer rouvrirait la connexion.
  const recoitJeu = useRef(jeu.recoit);
  recoitJeu.current = jeu.recoit;
  const enPartie = useRef(jeu.courante);

  const ecoute = useCallback(
    (evenement: Evenement) => {
      switch (evenement.type) {
        case "joined":
          tableau.vide();
          evenement.history
            .filter(estMorceau)
            .forEach((morceau) => tableau.ajoute(morceau));
          setVersion((v) => v + 1);
          setRoom(evenement.room);
          setJoueurs(evenement.players);
          setMeneur(evenement.leader);
          setEtat("salon");
          if (contexte.hote) {
            annonceSalon(evenement.room, contexte.hote);
          } else if (location.pathname !== `/${evenement.room}`) {
            history.replaceState(null, "", `/${evenement.room}`);
          }
          break;
        case "presence":
          setJoueurs(evenement.players);
          setMeneur(evenement.leader);
          break;
        case "message": {
          const p = enPartie.current.current;
          if (estMessageJeu(evenement.data)) {
            recoitJeu.current(evenement.from, evenement.data);
          } else if (
            estMorceau(evenement.data) &&
            (!p ||
              p.phase === "fin" ||
              (p.phase === "dessin" && evenement.from === p.dessinateur))
          ) {
            recus.emet(evenement.data);
          }
          break;
        }
        case "cleared":
          tableau.vide();
          setVersion((v) => v + 1);
          break;
        case "error":
          setErreur(`erreur_${evenement.code}` as Cle);
          setEtat("erreur");
          break;
        case "deconnecte":
          setEtat("reconnexion");
          break;
      }
    },
    [contexte.hote, recus, tableau],
  );

  useEffect(() => {
    const ouvert = new Relais(
      demande,
      "scribble",
      { id: moi, name: nom },
      ecoute,
    );
    relais.current = ouvert;
    return () => ouvert.ferme();
  }, [demande, ecoute, moi, nom]);

  const lien = room ? `${location.origin}/${room}` : null;
  const { partie } = jeu;
  const enCours = partie?.phase === "choix" || partie?.phase === "dessin";

  useEffect(() => {
    if (!enCours) {
      return;
    }
    const minuterie = window.setInterval(() => setMaintenant(Date.now()), 250);
    return () => window.clearInterval(minuterie);
  }, [enCours]);

  const dessinable =
    !partie ||
    partie.phase === "fin" ||
    (partie.phase === "dessin" && partie.dessinateur === moi);
  const devine =
    partie?.phase === "dessin" &&
    partie.devineurs.includes(moi) &&
    !partie.trouve.includes(moi);

  return (
    <main className="salon">
      <header className="bandeau">
        <h1>{texte(lang, "titre")}</h1>
        {room && <span className="code">{room}</span>}
        {!contexte.hote && lien && (
          <button
            className="discret"
            onClick={() =>
              void navigator.clipboard?.writeText(lien).then(() => {
                setCopie(true);
                window.setTimeout(() => setCopie(false), 1500);
              })
            }
          >
            {texte(lang, copie ? "copie" : "copier")}
          </button>
        )}
      </header>

      {etat === "erreur" && erreur ? (
        <section className="alerte">
          <p>{texte(lang, erreur)}</p>
          <button
            onClick={() => {
              setErreur(null);
              setEtat("connexion");
              setDemande(null);
            }}
          >
            {texte(lang, "nouveau")}
          </button>
        </section>
      ) : (
        <div className="jeu">
          <div className="zone">
            {etat !== "salon" && (
              <p className="etat">
                {texte(
                  lang,
                  etat === "reconnexion" ? "reconnexion" : "connexion",
                )}
              </p>
            )}
            {etat === "salon" && (
              <Consigne
                lang={lang}
                partie={partie}
                moi={moi}
                estMeneur={jeu.estMeneur}
                restantMs={
                  partie
                    ? Math.max(0, partie.restantMs - (maintenant - jeu.recuA))
                    : 0
                }
                nbJoueurs={joueurs.length}
                erreur={jeu.erreur}
                onLance={jeu.lance}
                onChoisis={jeu.choisis}
              />
            )}
            <Board
              tableau={tableau}
              actif={dessinable}
              couleur={couleur}
              epaisseur={epaisseur}
              version={version}
              auteur={moi}
              onMorceau={(morceau) => relais.current?.envoie(morceau, true)}
              recus={recus}
            />
            {devine && (
              <Devinette
                lang={lang}
                proche={jeu.proche}
                onDevine={jeu.devine}
              />
            )}
            <div className={dessinable ? "outils" : "outils cache"}>
              {COULEURS.map((c) => (
                <button
                  key={c}
                  className={c === couleur ? "couleur choisie" : "couleur"}
                  style={{ background: c }}
                  aria-label={c}
                  onClick={() => setCouleur(c)}
                />
              ))}
              <span className="separateur" />
              {EPAISSEURS.map((e) => (
                <button
                  key={e}
                  className={
                    e === epaisseur ? "epaisseur choisie" : "epaisseur"
                  }
                  aria-label={`${texte(lang, "epaisseur")} ${e}`}
                  onClick={() => setEpaisseur(e)}
                >
                  <span
                    style={{ width: `${e * 600}px`, height: `${e * 600}px` }}
                  />
                </button>
              ))}
              <span className="separateur" />
              <button
                className="discret"
                onClick={() => {
                  relais.current?.efface();
                  tableau.vide();
                  setVersion((v) => v + 1);
                }}
              >
                {texte(lang, "effacer")}
              </button>
            </div>
          </div>
          <aside className="joueurs">
            {partie ? (
              <Scores
                lang={lang}
                partie={partie}
                joueurs={joueurs}
                moi={moi}
                meneur={meneur}
              />
            ) : (
              <>
                <h2>{texte(lang, "joueurs")}</h2>
                <ul>
                  {joueurs.map((j) => (
                    <li key={j.id} className={j.id === moi ? "moi" : undefined}>
                      {j.name}
                      {j.id === meneur && (
                        <span className="meneur">
                          {" "}
                          · {texte(lang, "meneur")}
                        </span>
                      )}
                    </li>
                  ))}
                </ul>
              </>
            )}
          </aside>
        </div>
      )}
    </main>
  );
}
